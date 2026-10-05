package com.nguoncgenres

import java.net.URI
import java.net.URLEncoder

internal fun interface NguonCTransport {
    suspend fun request(url: String, body: String?, headers: Map<String, String>): String
}

/** Shared by the CloudStream adapter and live tests, including the playback handshake. */
internal class NguonCApi(private val transport: NguonCTransport) {
    private val mapper = NguonCParsing.mapper
    private val headers = mapOf("User-Agent" to NguonCParsing.USER_AGENT, "Accept" to "application/json")

    suspend fun list(path: String, page: Int = 1): NguonCList {
        require(path.startsWith("/films/"))
        val separator = if ('?' in path) '&' else '?'
        val result = mapper.readValue(transport.request("${NguonCParsing.BASE}/api$path${separator}page=$page", null, headers), NguonCList::class.java)
        check(result.status == "success") { result.message ?: "NguonC catalog request failed" }
        return result
    }

    suspend fun search(query: String): NguonCList = list("/films/search?keyword=${URLEncoder.encode(query.trim(), "UTF-8")}")

    suspend fun detail(slug: String): NguonCMovie {
        val result = mapper.readValue(transport.request("${NguonCParsing.BASE}/api/film/${NguonCParsing.slug(slug)}", null, headers), NguonCDetail::class.java)
        check(result.status == "success") { result.message ?: "NguonC detail request failed" }
        return checkNotNull(result.movie) { "NguonC returned no movie" }
    }

    suspend fun streamC(embed: String): String {
        require(NguonCParsing.isStreamC(embed)) { "Unsupported StreamC URL" }
        val origin = URI(embed).let { "${it.scheme}://${it.authority}" }
        val requestHeaders = headers + mapOf("Referer" to embed, "Origin" to origin, "Content-Type" to "application/json")
        suspend fun post(values: Map<String, Any>): com.fasterxml.jackson.databind.JsonNode {
            val result = mapper.readTree(transport.request(embed, mapper.writeValueAsString(values), requestHeaders))
            check(!result.hasNonNull("error")) { "StreamC: ${result.path("error").asText()}" }
            return result
        }
        val common = mapOf<String, Any>(
            "playlist_format" to "hls", "pretty_url" to true, "path_chunks" to true,
            "frame_origins" to listOf(NguonCParsing.BASE),
        )
        val bootstrap = post(common + mapOf(
            "action" to "bootstrap", "referrer" to "${NguonCParsing.BASE}/",
            "request_grant" to true, "bootstrap_format" to "json",
        ))
        check(!bootstrap.path("turnstileEnabled").asBoolean(false)) {
            "StreamC requires browser verification for this server"
        }
        val grant = if (bootstrap.path("preissued").hasNonNull("playlist")) bootstrap.path("preissued") else {
            val token = bootstrap.path("bootstrap").asText()
            check(token.isNotBlank()) { "StreamC returned no playback grant" }
            post(common + mapOf("action" to "issue", "bootstrap" to token, "turnstile_response" to ""))
        }
        check(grant.path("playlistFormat").asText("hls") == "hls") { "StreamC did not return standard HLS" }
        val url = NguonCParsing.absolute(grant.path("playlist").asText(), embed)
        return checkNotNull(url) { "StreamC returned no HLS URL" }
    }
}
