package com.motchill

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/** Opt-in: exercises the SAME parser as the plugin against public pages and media. */
class MotChillLiveTest {
    private val base = "https://motphimchilll.fun"
    private fun fetch(url: String, referer: String = "$base/"): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        connection.setRequestProperty("Referer", referer)
        return try {
            check(connection.responseCode in 200..299) { "$url returned ${connection.responseCode}" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }
    private fun page(url: String) = Jsoup.parse(fetch(url), url)

    @Test fun liveCatalogAndLanternsPlaybackContract() {
        assumeTrue("Set MOTCHILL_LIVE_TEST=1 for network checks", System.getenv("MOTCHILL_LIVE_TEST") == "1")
        val first = MotChillParsing.listing(page("$base/the-loai/kinh-di?page=1"))
        val second = MotChillParsing.listing(page("$base/the-loai/kinh-di?page=2"))
        assertTrue(first.cards.isNotEmpty())
        assertTrue(first.hasNext)
        assertTrue(second.cards.isNotEmpty())
        assertNotEquals(first.cards.map { it.url }, second.cards.map { it.url })
        val search = MotChillParsing.listing(page("$base/?search=Lanterns"))
        val url = "$base/phim/luc-luong-lanterns-phan-1"
        assertTrue(search.cards.any { it.url == url })
        val detail = MotChillParsing.detail(page(url))
        assertTrue(detail.series)
        assertTrue(detail.actors.isNotEmpty())
        assertFalse(detail.plot.isNullOrBlank())
        val episodes = MotChillParsing.episodes(page(requireNotNull(detail.watchUrl)))
        assertEquals((1..8).toList(), episodes.map { it.number })
        assertEquals(2, episodes.first().sources.size)
        // Every episode page must expose at least one supported player, including episode 8.
        episodes.forEach { episode ->
            episode.sources.forEach { source ->
                val players = MotChillParsing.players(page(source.url))
                assertTrue("No player for ${source.url}", players.isNotEmpty())
                assertTrue(players.any { MotChillParsing.isMedia(it.url) || MotChillParsing.isVsmov(it.url) || MotChillParsing.wrappedStream(it.url) != null })
            }
        }
        var playable = 0
        // Probe both language variants of episode 1, plus the final bilingual episode.
        (episodes.first().sources + episodes.last().sources).forEach { source ->
            val players = MotChillParsing.players(page(source.url))
            players.forEach { player ->
                val embed = if (MotChillParsing.isVsmov(player.url)) MotChillParsing.vsmov(page(player.url)) else null
                val stream = embed?.stream ?: MotChillParsing.wrappedStream(player.url)
                    ?: player.url.takeIf { MotChillParsing.isMedia(it) } ?: return@forEach
                try {
                    val manifest = fetch(stream, player.url)
                    assertTrue(manifest.startsWith("#EXTM3U"))
                    assertTrue(manifest.contains("#EXTINF") || manifest.contains("#EXT-X-STREAM-INF"))
                    embed?.subtitles?.forEach { assertTrue(fetch(it.url, player.url).trimStart().startsWith("WEBVTT")) }
                    println("PLAYABLE ${source.server}: $stream; subtitles=${embed?.subtitles?.size ?: 0}")
                    playable++
                } catch (error: Exception) {
                    println("UPSTREAM FAILURE ${source.server}: ${error.message}")
                }
            }
        }
        assertTrue("No reachable Lanterns HLS source", playable > 0)
        println("PASS: category pagination, search, metadata, all 8 episodes / 15 language entries; $playable reachable playlists")
    }
}
