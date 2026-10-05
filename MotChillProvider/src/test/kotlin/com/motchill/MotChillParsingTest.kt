package com.motchill

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class MotChillParsingTest {
    private val base = "https://motphimchilll.fun"

    @Test fun listingUsesLazyImagesAndExcludesSidebar() {
        val doc = Jsoup.parse("""
            <div class="list-films"><li class="item"><span class="label">Hoàn Tất (8/8) Vietsub</span>
            <a href="/phim/lanterns" title="Lanterns"><img data-original="/poster.webp" src="placeholder.jpg"></a>
            <div class="name">Lanterns 2026</div></li></div>
            <aside><li class="item"><a href="/phim/other" title="Not a result"></a></li></aside>
            <div class="pagination"><a rel="next" href="?page=2">Next</a></div>
        """, base)
        val result = MotChillParsing.listing(doc)
        assertEquals(1, result.cards.size)
        val card = result.cards.single()
        assertEquals("$base/poster.webp", card.poster)
        assertEquals("$base/phim/lanterns", card.url)
        assertEquals(2026, card.year)
        assertEquals(8, card.episodeCount)
        assertTrue(card.series)
        assertTrue(result.hasNext)
        assertFalse(MotChillParsing.listing(Jsoup.parse("<div class='list-films'></div>")).hasNext)
    }

    @Test fun movieLabelDoesNotInventEpisodeCount() {
        val doc = Jsoup.parse("""<div class="list-films"><li class="item"><span class="label">Full Vietsub</span>
            <a href="/phim/movie" title="Movie"><img src="//img.example/poster.jpg"></a></li></div>""", base)
        val card = MotChillParsing.listing(doc).cards.single()
        assertFalse(card.series)
        assertNull(card.episodeCount)
        assertEquals("https://img.example/poster.jpg", card.poster)
    }

    @Test fun detailUsesFullSynopsisAndScopedMetadata() {
        val doc = Jsoup.parse("""
            <meta property="og:image" content="/poster.webp"><meta property="og:image" content="/backdrop.webp">
            <script type="application/ld+json">{"@type":"TVSeries"}</script>
            <script type="application/ld+json">{"@type":"Article","articleBody":"The full synopsis."}</script>
            <nav><a href="/the-loai/phim-18">Unrelated menu</a></nav>
            <div id="page-info"><div class="info" itemtype="https://schema.org/TVSeries">
              <h1><span class="title">Lanterns</span></h1><div class="poster"><img src="/poster.webp"></div>
              <dl><dt>Năm sản xuất:</dt><dd>2026</dd><dt>Thời lượng:</dt><dd>55 phút</dd>
              <dt>Tình trạng:</dt><dd>Hoàn thành</dd></dl>
              <a href="/the-loai/bi-an">Bí ẩn</a><a href="/dien-vien/actor">Actor</a>
              <a class="btn-stream-link" href="/phim/lanterns/tap-1-123">Watch</a>
            </div></div>
        """, base)
        val detail = MotChillParsing.detail(doc)
        assertEquals("Lanterns", detail.title)
        assertEquals("The full synopsis.", detail.plot)
        assertEquals(listOf("Bí ẩn"), detail.tags)
        assertEquals(listOf("Actor"), detail.actors)
        assertEquals(55, detail.duration)
        assertTrue(detail.series)
        assertTrue(detail.completed)
        assertFalse(detail.adult)
        assertEquals("$base/phim/lanterns/tap-1-123", detail.watchUrl)
    }

    @Test fun episodesGroupLanguagesAndSortNumerically() {
        val doc = Jsoup.parse("""
          <div class="control-box"><div class="server-episode-block">Vietsub #1:</div><div class="list-episode">
          <a href="/phim/show/tap-10-10">Tập 10</a><a href="/phim/show/tap-2-2">Tập 2</a>
          <a href="/phim/show/tap-1-1">Tập 01</a></div></div>
          <div class="control-box"><div class="server-episode-block">Song ngữ:</div><div class="list-episode">
          <a href="/phim/show/tap-1-100">Tập 1</a><a href="/phim/show/tap-1-100">Tập 1</a></div></div>
        """, base)
        val episodes = MotChillParsing.episodes(doc)
        assertEquals(listOf(1, 2, 10), episodes.map { it.number })
        assertEquals(listOf("Vietsub #1", "Song ngữ"), episodes.first().sources.map { it.server })
        assertEquals(1, episodes.last().sources.size)
    }

    @Test fun parsesOnlyPlayerLinksAndUnwrapsKnownHost() {
        val doc = Jsoup.parse("""
          <li class="streaming-server" data-link="https://cdn.example/index.m3u8?a=1&amp;b=2" data-type="m3u8">Hà Nội</li>
          <li class="streaming-server" data-link="javascript:evil()" data-type="embed">Bad</li>
          <a data-link="https://ads.example/video.mp4">Advertisement</a>
        """, base)
        assertEquals("https://cdn.example/index.m3u8?a=1&b=2", MotChillParsing.players(doc).single().url)
        assertEquals("https://cdn.example/index.m3u8", MotChillParsing.wrappedStream("https://player.phimapi.com/player/?url=https%3A%2F%2Fcdn.example%2Findex.m3u8"))
        assertNull(MotChillParsing.wrappedStream("https://other.example/?url=https://cdn.example/index.m3u8"))
        assertFalse(MotChillParsing.isVsmov("https://streamvsmov.com.evil.example/video/123"))
    }

    @Test fun vsmovResolvesManifestAndSubtitlePaths() {
        val doc = Jsoup.parse("""
          <div id="rp-player" data-hash="abc-123"></div><script>
          const playerOptions = { subtitles: [{"code":"vie","url":"/video/abc-123/sub.vtt"},
          {"code":"eng","url":"/video/abc-123/en.vtt"}], audios: [], enableSignedUrl: false, signedMasterUrl: "" };
          </script>
        """, "https://v8.streamvsmov.com/video/abc-123")
        val embed = MotChillParsing.vsmov(doc)
        assertEquals("https://v8.streamvsmov.com/stream/abc-123/master.m3u8", embed.stream)
        assertEquals(listOf("Vietnamese", "English"), embed.subtitles.map { it.language })
        assertEquals("https://v8.streamvsmov.com/video/abc-123/sub.vtt", embed.subtitles.first().url)
    }

    @Test fun signedManifestMustUseProvidedUrl() {
        val doc = Jsoup.parse("""<div id="rp-player" data-hash="abc"></div>
          <script>const playerOptions = { enableSignedUrl: true, signedMasterUrl: "/signed/master.m3u8?token=public" };</script>
        """, "https://v8.streamvsmov.com/video/abc")
        assertEquals("https://v8.streamvsmov.com/signed/master.m3u8?token=public", MotChillParsing.vsmov(doc).stream)
        assertNull(MotChillParsing.vsmov(Jsoup.parse("<script>enableSignedUrl: true</script>", base)).stream)
    }
}
