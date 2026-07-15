package com.kkphimgenres

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KKPhimParsingTest {
    @Test
    fun `normalizes relative and absolute image paths`() {
        assertEquals(
            "https://phimimg.com/uploads/poster.webp",
            KKPhimParsing.absoluteImageUrl("uploads/poster.webp", "https://phimimg.com"),
        )
        assertEquals(
            "https://cdn.example/poster.webp",
            KKPhimParsing.absoluteImageUrl("https://cdn.example/poster.webp"),
        )
        assertEquals(
            "https://cdn.example/poster.webp",
            KKPhimParsing.absoluteImageUrl("//cdn.example/poster.webp"),
        )
        assertNull(KKPhimParsing.absoluteImageUrl(null))
    }

    @Test
    fun `extracts episode numbers and ignores full movies`() {
        assertEquals(12, KKPhimParsing.episodeNumber("Tập 12", "tap-12"))
        assertEquals(3, KKPhimParsing.episodeNumber(null, "tap-03"))
        assertNull(KKPhimParsing.episodeNumber("Full", "full"))
    }

    @Test
    fun `creates the same grouping key across language servers`() {
        val vietsub = KKPhimEpisode(name = "Tập 7", slug = "tap-7")
        val dubbed = KKPhimEpisode(name = "Tập 07", slug = "tap-07")

        assertEquals(KKPhimParsing.episodeKey(vietsub), KKPhimParsing.episodeKey(dubbed))
    }

    @Test
    fun `normalizes numeric tracking ids`() {
        assertEquals("1234", KKPhimParsing.externalId(1234.0))
        assertEquals("tt1234567", KKPhimParsing.externalId("tt1234567"))
        assertNull(KKPhimParsing.externalId(null))
    }

    @Test
    fun `maps KKPhim language labels to cloudstream badges`() {
        assertEquals(
            KKPhimSearchBadges(subbed = true, dubbed = false, episodeCount = 8),
            KKPhimParsing.searchBadges("Vietsub", "Tập 8", isSeries = true),
        )
        assertEquals(
            KKPhimSearchBadges(subbed = false, dubbed = true, episodeCount = 12),
            KKPhimParsing.searchBadges("Thuyết Minh", "Hoàn Tất (12/12)", isSeries = true),
        )
        assertEquals(
            KKPhimSearchBadges(subbed = true, dubbed = true, episodeCount = 16),
            KKPhimParsing.searchBadges("Vietsub + Lồng Tiếng", "Hoàn Tất (16/16)", isSeries = true),
        )
    }

    @Test
    fun `does not report episodes for movies or language badges for trailers`() {
        assertEquals(
            KKPhimSearchBadges(subbed = true, dubbed = false, episodeCount = null),
            KKPhimParsing.searchBadges("Vietsub", "Full", isSeries = false),
        )
        assertEquals(
            KKPhimSearchBadges(subbed = false, dubbed = false, episodeCount = null),
            KKPhimParsing.searchBadges("Vietsub + Thuyết Minh", "Trailer", isSeries = true),
        )
    }

    @Test
    fun `keeps only useful cam quality on cards`() {
        assertEquals("CAM", KKPhimParsing.cardQuality("cam"))
        assertNull(KKPhimParsing.cardQuality("FHD"))
        assertNull(KKPhimParsing.cardQuality("HD"))
    }
}
