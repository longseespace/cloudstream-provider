package com.motchill

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLDecoder

data class MotChillCard(
    val title: String, val url: String, val poster: String?, val year: Int?,
    val label: String, val series: Boolean, val episodeCount: Int?,
)
data class MotChillPage(val cards: List<MotChillCard>, val hasNext: Boolean)
data class MotChillEpisodeSource(val url: String = "", val server: String = "")
data class MotChillEpisode(val name: String, val number: Int?, val sources: List<MotChillEpisodeSource>)
data class MotChillPlayer(val url: String, val type: String, val name: String)
data class MotChillSubtitle(val language: String, val url: String)
data class MotChillEmbed(val stream: String?, val subtitles: List<MotChillSubtitle>)
data class MotChillDetail(
    val title: String, val poster: String?, val backdrop: String?, val year: Int?,
    val plot: String?, val tags: List<String>, val actors: List<String>,
    val duration: Int?, val series: Boolean, val completed: Boolean,
    val watchUrl: String?, val adult: Boolean,
)

/** Pure parsing: no CloudStream/Android dependency, also used by the live contract tests. */
object MotChillParsing {
    private val mapper = jacksonObjectMapper()
    private val number = Regex("\\d+")

    fun absolute(base: String, value: String?): String? = runCatching {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        URI(base).resolve(raw).takeIf { it.scheme in listOf("https", "http") }?.toString()
    }.getOrNull()

    fun listing(doc: Document): MotChillPage {
        // Scope to the main result list; sidebar recommendations are not search results.
        val cards = doc.select(".list-films .item").mapNotNull { item ->
            val link = item.selectFirst("a[href*=/phim/]") ?: return@mapNotNull null
            val url = absolute(doc.baseUri(), link.attr("href")) ?: return@mapNotNull null
            val title = link.attr("title").ifBlank { item.selectFirst(".name")?.text().orEmpty() }
            if (title.isBlank()) return@mapNotNull null
            val img = item.selectFirst("img")
            val poster = listOf("data-original", "data-src", "src").firstNotNullOfOrNull { attr ->
                absolute(doc.baseUri(), img?.attr(attr))
            }
            val label = item.selectFirst(".label")?.text().orEmpty()
            val series = Regex("(?i)tập|hoàn tất|hoàn thành").containsMatchIn(label)
            val count = if (series) number.find(label)?.value?.toIntOrNull() else null
            val year = Regex("(?:19|20)\\d{2}$").find(item.selectFirst(".name")?.text().orEmpty())?.value?.toIntOrNull()
            MotChillCard(title, url, poster, year, label, series, count)
        }.distinctBy { it.url }
        return MotChillPage(cards, doc.selectFirst(".pagination a[rel=next], .pagination a.next") != null)
    }

    fun detail(doc: Document): MotChillDetail {
        val info = doc.selectFirst("#page-info .info") ?: error("MotChill: movie information is missing")
        val fields = info.select("dt").associate { it.text().trim().removeSuffix(":") to it.nextElementSibling()?.text().orEmpty() }
        val json = doc.select("script[type=application/ld+json]").mapNotNull {
            runCatching { mapper.readTree(it.data()) }.getOrNull()
        }
        val schema = json.firstOrNull { it.path("@type").asText() in listOf("Movie", "TVSeries") }
        val article = json.firstOrNull { it.path("@type").asText() == "Article" }
        val title = info.selectFirst("h1 .title, h1")?.text()?.takeIf { it.isNotBlank() }
            ?: error("MotChill: movie title is missing")
        val plot = article?.path("articleBody")?.asText()?.takeIf { it.isNotBlank() }
            ?: doc.select("#info-film .tab p").firstOrNull { it.text().isNotBlank() && it.select("a, img").isEmpty() }?.text()
        return MotChillDetail(
            title = title,
            poster = absolute(doc.baseUri(), info.selectFirst(".poster img")?.attr("src")),
            backdrop = absolute(doc.baseUri(), doc.select("meta[property=og:image]").lastOrNull()?.attr("content")),
            year = fields["Năm sản xuất"]?.toIntOrNull(), plot = plot,
            tags = info.select("a[href*=/the-loai/]").map { it.text() }.distinct(),
            actors = info.select("a[href*=/dien-vien/]").map { it.text() }.distinct(),
            duration = number.find(fields["Thời lượng"].orEmpty())?.value?.toIntOrNull(),
            series = schema?.path("@type")?.asText() == "TVSeries" || info.attr("itemtype").endsWith("TVSeries"),
            completed = fields["Tình trạng"].orEmpty().contains("Hoàn", true),
            watchUrl = absolute(doc.baseUri(), info.selectFirst("a.btn-stream-link")?.attr("href")),
            adult = info.select("a[href*=/the-loai/phim-18]").isNotEmpty(),
        )
    }

    fun episodes(doc: Document): List<MotChillEpisode> {
        data class Group(val name: String, val number: Int?, val sources: MutableList<MotChillEpisodeSource>)
        val groups = linkedMapOf<String, Group>()
        doc.select(".control-box").forEach { block ->
            val server = block.selectFirst(".server-episode-block")?.text()?.trim()?.removeSuffix(":") ?: return@forEach
            block.select(".list-episode a[href]").forEach episode@{ link ->
                val url = absolute(doc.baseUri(), link.attr("href")) ?: return@episode
                val title = link.text().trim().ifBlank { link.attr("title") }
                val episodeNumber = number.find(title)?.value?.toIntOrNull()
                val key = episodeNumber?.toString() ?: title.lowercase()
                val group = groups.getOrPut(key) { Group(title, episodeNumber, mutableListOf()) }
                if (group.sources.none { it.url == url }) group.sources += MotChillEpisodeSource(url, server)
            }
        }
        return groups.values.sortedBy { it.number ?: Int.MAX_VALUE }
            .map { MotChillEpisode(it.name, it.number, it.sources.toList()) }
    }

    fun players(doc: Document): List<MotChillPlayer> = doc.select(".streaming-server[data-link]").mapNotNull {
        val url = absolute(doc.baseUri(), it.attr("data-link")) ?: return@mapNotNull null
        MotChillPlayer(url, it.attr("data-type").lowercase(), it.text().ifBlank { "MotChill" })
    }.distinctBy { it.url }

    fun wrappedStream(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        // Only unwrap the public player URL parameter on the known KKPhim embed host.
        if (uri.host != "player.phimapi.com") return null
        val raw = uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("url=") }?.substringAfter('=') ?: return null
        val decoded = URLDecoder.decode(raw, "UTF-8")
        return absolute(url, decoded)?.takeIf { isMedia(it) }
    }

    fun isMedia(url: String): Boolean = Regex("(?i)\\.(m3u8|mp4)(?:[?#]|$)").containsMatchIn(url)

    fun isVsmov(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull().orEmpty()
        return host == "streamvsmov.com" || host.endsWith(".streamvsmov.com")
    }

    fun vsmov(doc: Document): MotChillEmbed {
        val script = doc.select("script:not([src])").joinToString("\n") { it.data() }
        val signed = Regex("""signedMasterUrl\s*:\s*["']([^"']+)["']""").find(script)?.groupValues?.get(1)
        val signedEnabled = Regex("""enableSignedUrl\s*:\s*true""").containsMatchIn(script)
        val hash = doc.selectFirst("#rp-player[data-hash]")?.attr("data-hash")
            ?: Regex("""(?:const|var|let)\s+videoHash\s*=\s*["']([^"']+)["']""").find(script)?.groupValues?.get(1)
        val stream = if (signedEnabled) absolute(doc.baseUri(), signed)
            else hash?.takeIf { Regex("[a-zA-Z0-9-]+").matches(it) }?.let { absolute(doc.baseUri(), "/stream/$it/master.m3u8") }
        val subtitleJson = Regex("""subtitles\s*:\s*(\[[\s\S]*?])\s*,\s*audios""").find(script)?.groupValues?.get(1)
        val subtitles = runCatching { mapper.readTree(subtitleJson ?: "[]").mapNotNull { sub ->
            val url = absolute(doc.baseUri(), sub.path("url").asText()) ?: return@mapNotNull null
            val code = sub.path("code").asText()
            MotChillSubtitle(when (code) { "vie", "vi" -> "Vietnamese"; "eng", "en" -> "English"; else -> code.ifBlank { sub.path("name").asText("Subtitles") } }, url)
        } }.getOrDefault(emptyList())
        return MotChillEmbed(stream, subtitles)
    }
}
