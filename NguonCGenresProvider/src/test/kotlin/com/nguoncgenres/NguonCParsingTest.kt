package com.nguoncgenres

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NguonCParsingTest {
    private val mapper = NguonCParsing.mapper

    @Test fun readsActualApiShapeAndNullableFields() {
        val response = mapper.readValue("""{
          "status":"success","movie":{"name":"Example","year":"2026","casts":null,
          "tmdb":{"id":42,"type":"tv","season":0},"episodes":null,
          "category":{"random-key":{"group":{"name":"Thể loại"},"list":[{"name":"Kinh dị"}]}},
          "some_future_field":true}}
        """, NguonCDetail::class.java)
        val movie = requireNotNull(response.movie)
        assertEquals(2026, movie.year)
        assertEquals("42", movie.tmdb?.id)
        assertEquals(listOf("Kinh dị"), NguonCParsing.category(movie, "Thể loại"))
        assertTrue(NguonCParsing.isSeries(movie))
        assertTrue(NguonCParsing.episodes(movie).isEmpty())
    }

    @Test fun groupsServersAndSortsWithoutMergingSpecialOrFractionalEpisodes() {
        fun ep(name: String, url: String) = NguonCEpisode(name = name, embed = url)
        val movie = NguonCMovie(episodes = listOf(
            NguonCServer("Vietsub", listOf(ep("10", "https://a/10"), ep("02", "https://a/2"), ep("1", "https://a/1"), ep("1.5", "https://a/1-5"), ep("Special", "https://a/special"))),
            NguonCServer("Lồng tiếng", listOf(ep("Tập 1", "https://b/1"), ep("Tập 2", "https://b/2"))),
        ))
        val episodes = NguonCParsing.episodes(movie)
        assertEquals(listOf("episode:1", "episode:1.5", "episode:2", "episode:10", "special:special"), episodes.map { it.key })
        assertEquals(2, episodes.first().sources.size)
        assertNull(episodes[1].number)
        assertEquals(10, episodes[3].number)
    }

    @Test fun handlesEmptyAndInvalidStreamUrls() {
        val movie = NguonCMovie(episodes = listOf(NguonCServer(items = listOf(
            NguonCEpisode(name = "1", m3u8 = "", embed = "https://embed.example/1"),
            NguonCEpisode(name = "2", embed = "javascript:alert(1)"),
        ))))
        assertEquals(listOf("episode:1"), NguonCParsing.episodes(movie).map { it.key })
        assertEquals("https://img.nguonc.com/images/a.jpg", NguonCParsing.poster(NguonCMovie(thumb = "/images/a.jpg", poster = "")))
        assertFalse(NguonCParsing.isStreamC("https://streamc.xyz.evil.example/embed.php"))
    }

    @Test fun derivesUsefulBadgesWithoutInventingEpisodeCountsForMovies() {
        val series = NguonCMovie(currentEpisode = "Hoàn tất (6/6)", language = "Vietsub + Thuyết Minh")
        assertEquals(6, NguonCParsing.currentCount(series))
        assertTrue(NguonCParsing.subbed(series))
        assertTrue(NguonCParsing.dubbed(series))
        assertNull(NguonCParsing.currentCount(series.copy(currentEpisode = "FULL", totalEpisodes = 1)))
        assertFalse(NguonCParsing.subbed(series.copy(currentEpisode = "Trailer")))
    }

    @Test fun paginationUsesServerTotalsRatherThanFilteredResultCount() {
        assertTrue(NguonCParsing.hasNext(NguonCList(paginate = NguonCPagination(1, 2, 10)), 1))
        assertFalse(NguonCParsing.hasNext(NguonCList(paginate = NguonCPagination(2, 2, 10)), 2))
        assertFalse(NguonCParsing.hasNext(NguonCList(), 1))
    }

    @Test fun adultMetadataIsDetected() {
        assertTrue(NguonCParsing.isAdult(NguonCMovie(category = mapOf("2" to
            NguonCCategory(NguonCNamed("Thể loại"), listOf(NguonCNamed("Phim 18+")))))))
    }

    @Test fun bootstrapUsesHlsAndReturnsFreshGrantOnEveryPlayback() = runBlocking {
        var requests = 0
        val api = NguonCApi { _, body, headers ->
            requests++
            assertEquals("https://embed13.streamc.xyz", headers["Origin"])
            assertEquals("hls", mapper.readTree(body).path("playlist_format").asText())
            """{"turnstileEnabled":false,"preissued":{"playlist":"https://embed13.streamc.xyz/grant-$requests","playlistFormat":"hls"}}"""
        }
        val embed = "https://embed13.streamc.xyz/embed.php?hash=test"
        assertNotEquals(api.streamC(embed), api.streamC(embed))
        assertEquals(2, requests)
    }

    @Test fun requestsIssueWhenBootstrapDoesNotPreissueGrant() = runBlocking {
        val actions = mutableListOf<String>()
        val api = NguonCApi { _, body, _ ->
            val request = mapper.readTree(body)
            actions += request.path("action").asText()
            if (actions.size == 1) """{"bootstrap":"public-session-grant","turnstileEnabled":false}"""
            else {
                assertEquals("public-session-grant", request.path("bootstrap").asText())
                """{"playlist":"https://embed13.streamc.xyz/playlist","playlistFormat":"hls"}"""
            }
        }
        api.streamC("https://embed13.streamc.xyz/embed.php?hash=test")
        assertEquals(listOf("bootstrap", "issue"), actions)
    }

    @Test fun reportsVerificationAndUnsupportedPlaylistInsteadOfEmittingBrokenLinks() = runBlocking {
        for (response in listOf(
            """{"turnstileEnabled":true}""",
            """{"preissued":{"playlist":"https://embed13.streamc.xyz/playlist","playlistFormat":"aesgcm-v2"}}""",
            """{"error":"website_blocked"}""",
        )) {
            val result = runCatching { NguonCApi { _, _, _ -> response }.streamC("https://embed13.streamc.xyz/embed.php?hash=test") }
            assertTrue(result.isFailure)
        }
    }

    @Test fun encodesSearchAndRejectsApiErrors() = runBlocking {
        val api = NguonCApi { url, _, _ ->
            assertTrue(url.contains("keyword=A+%26+B&page=1"))
            """{"status":"error","message":"Try later"}"""
        }
        assertEquals("Try later", runCatching { api.search("A & B") }.exceptionOrNull()?.message)
    }
}
