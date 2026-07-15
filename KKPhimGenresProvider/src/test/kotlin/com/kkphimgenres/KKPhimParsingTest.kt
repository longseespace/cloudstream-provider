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
}
