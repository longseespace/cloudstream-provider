package com.nguoncgenres

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class NguonCLiveTest {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private fun request(url: String, body: String? = null, headers: Map<String, String> = emptyMap()): ByteArray {
        val request = Request.Builder().url(url).header("User-Agent", NguonCParsing.USER_AGENT)
        headers.forEach { (key, value) -> request.header(key, value) }
        if (body != null) request.post(body.toRequestBody("application/json".toMediaType()))
        return client.newCall(request.build()).execute().use { response ->
            check(response.isSuccessful) { "${URI(url).host} returned HTTP ${response.code}" }
            requireNotNull(response.body).bytes()
        }
    }

    @Test fun liveCatalogEpisodesAndHls() = runBlocking {
        assumeTrue("Set NGUONC_LIVE_TEST=1 for network checks", System.getenv("NGUONC_LIVE_TEST") == "1")
        val api = NguonCApi { url, body, headers -> request(url, body, headers).toString(Charsets.UTF_8) }
        val first = api.list("/films/the-loai/kinh-di", 1)
        val second = api.list("/films/the-loai/kinh-di", 2)
        assertTrue(first.items.isNotEmpty())
        assertTrue(NguonCParsing.hasNext(first, 1))
        assertNotEquals(first.items.map { it.slug }, second.items.map { it.slug })
        val sample = first.items.first()
        assertTrue(api.search(requireNotNull(sample.name)).items.any { it.slug == sample.slug })
        val series = api.detail("lien-ket-van-menh")
        assertTrue(NguonCParsing.isSeries(series))
        assertTrue(NguonCParsing.category(series, "Thể loại").isNotEmpty())
        assertFalse(series.casts.isNullOrBlank())
        val episodes = NguonCParsing.episodes(series)
        assertEquals((1..6).toList(), episodes.map { it.number })
        assertTrue(episodes.first().sources.size >= 2)
        val movieCard = api.list("/films/danh-sach/phim-le").items.first()
        val movie = api.detail(requireNotNull(movieCard.slug))
        assertFalse(NguonCParsing.isSeries(movie))
        var playable = 0
        val failures = mutableListOf<String>()
        val sources = episodes.first().sources + episodes.last().sources.take(1) + NguonCParsing.episodes(movie).flatMap { it.sources }
        for (source in sources) {
            try {
                val url = if (NguonCParsing.isStreamC(source.url)) api.streamC(source.url) else source.url
                val ref = mapOf("Referer" to source.url)
                val playlist = request(url, headers = ref).toString(Charsets.UTF_8)
                assertTrue(playlist.startsWith("#EXTM3U"))
                assertTrue(playlist.contains("#EXTINF") || playlist.contains("#EXT-X-STREAM-INF"))
                // Verify media bytes, not just a URL or HTTP 200 HTML error page.
                var mediaBase = url
                var mediaPlaylist = playlist
                if (playlist.contains("#EXT-X-STREAM-INF")) {
                    mediaBase = URI(url).resolve(playlist.lineSequence().first { it.isNotBlank() && !it.startsWith('#') }).toString()
                    mediaPlaylist = request(mediaBase, headers = ref).toString(Charsets.UTF_8)
                }
                val segment = URI(mediaBase).resolve(mediaPlaylist.lineSequence().first { it.isNotBlank() && !it.startsWith('#') }).toString()
                val bytes = request(segment, headers = ref + ("Range" to "bytes=0-4095"))
                assertTrue("Segment empty", bytes.size > 188)
                assertFalse("HTML returned as media", bytes.take(100).toByteArray().toString(Charsets.UTF_8).contains("<html", true))
                playable++
                println("PASS ${source.server}: standard HLS and media segment (${bytes.size} bytes)")
            } catch (error: Exception) {
                failures += "${source.server}: ${error.message}"
            }
        }
        assertTrue("No playable sources: $failures", playable > 0)
        println("NguonC: catalog pagination, search, series grouping, movie metadata; $playable/${sources.size} HLS sources reachable. Failures: $failures")
    }
}
