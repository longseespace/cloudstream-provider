package com.motchill

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.util.concurrent.CancellationException

class MotChillProvider : MainAPI() {
    override var mainUrl = "https://motphimchilll.fun"
    override var name = "MotChill"
    override var lang = "vi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val categories = mainPageOf(
        "/danh-sach/phim-moi" to "Phim mới",
        "/danh-sach/phim-bo" to "Phim bộ",
        "/danh-sach/phim-le" to "Phim lẻ",
        "/danh-sach/phim-chieu-rap" to "Phim chiếu rạp",
        "/danh-sach/phim-thuyet-minh" to "Phim Thuyết Minh",
        "/the-loai/kinh-di" to "Kinh Dị",
        "/the-loai/hanh-dong" to "Hành Động",
        "/the-loai/bi-an" to "Bí Ẩn",
        "/the-loai/vien-tuong" to "Viễn Tưởng",
        "/the-loai/phieu-luu" to "Phiêu Lưu",
        "/the-loai/hinh-su" to "Hình Sự",
        "/the-loai/chinh-kich" to "Chính kịch",
        "/the-loai/tinh-cam" to "Tình Cảm",
        "/the-loai/tam-ly" to "Tâm Lý",
        "/the-loai/hai-huoc" to "Hài Hước",
        "/the-loai/co-trang" to "Cổ Trang",
        "/the-loai/gia-dinh" to "Gia Đình",
        "/the-loai/khoa-hoc" to "Khoa Học",
        "/the-loai/chien-tranh" to "Chiến Tranh",
        "/the-loai/hoat-hinh" to "Hoạt Hình",
        "/the-loai/tai-lieu" to "Tài Liệu",
        "/the-loai/hoc-duong" to "Học Đường",
        "/the-loai/vo-thuat" to "Võ Thuật",
        "/the-loai/the-thao" to "Thể Thao",
        "/the-loai/am-nhac" to "Âm Nhạc",
        "/the-loai/tv-shows" to "TV Shows",
        "/the-loai/than-thoai" to "Thần Thoại",
        "/the-loai/phim-yeu-thich" to "Phim yêu thích",
        "/the-loai/kinh-dien" to "Kinh Điển",
    )
    override val mainPage: List<MainPageData>
        get() = if (settingsForProvider.enableAdult) categories + mainPageOf("/the-loai/phim-18" to "Phim 18+") else categories

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (request.data == "/the-loai/phim-18" && !settingsForProvider.enableAdult) {
            return newHomePageResponse(request, emptyList<SearchResponse>(), false)
        }
        val result = MotChillParsing.listing(app.get("$mainUrl${request.data}", params = mapOf("page" to page.toString())).document)
        return newHomePageResponse(request, result.cards.map { it.toSearchResponse() }, result.hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()
        return MotChillParsing.listing(app.get("$mainUrl/", params = mapOf("search" to query.trim())).document)
            .cards.map { it.toSearchResponse() }
    }

    private fun MotChillCard.toSearchResponse(): SearchResponse =
        newAnimeSearchResponse(title, url, if (series) TvType.TvSeries else TvType.Movie) {
            posterUrl = poster
            year = this@toSearchResponse.year
            // Do not label every title HD when the listing provides no resolution.
            quality = Regex("(?i)\\b(CAM|HD|FHD|4K)\\b").find(label)?.value?.let { getQualityFromString(it) }
            addDubStatus(
                dubExist = label.contains("Lồng", true) || label.contains("Thuyết", true),
                subExist = label.contains("Vietsub", true) || label.contains("Song ngữ", true),
                dubEpisodes = episodeCount, subEpisodes = episodeCount,
            )
        }

    override suspend fun load(url: String): LoadResponse {
        val movie = MotChillParsing.detail(app.get(url).document)
        if (movie.adult && !settingsForProvider.enableAdult) throw ErrorLoadingException("Adult content is disabled")
        val watch = movie.watchUrl ?: throw ErrorLoadingException("MotChill has no released episodes for this title")
        val episodes = MotChillParsing.episodes(app.get(watch).document)
        val result = if (movie.series) {
            if (episodes.isEmpty()) throw ErrorLoadingException("MotChill returned no episodes")
            newTvSeriesLoadResponse(movie.title, url, TvType.TvSeries, episodes.map { entry ->
                newEpisode(entry.sources) { name = entry.name; episode = entry.number; season = 1 }
            }) { showStatus = if (movie.completed) ShowStatus.Completed else ShowStatus.Ongoing }
        } else {
            val sources = episodes.flatMap { it.sources }.ifEmpty { listOf(MotChillEpisodeSource(watch, "MotChill")) }
            newMovieLoadResponse(movie.title, url, TvType.Movie, sources)
        }
        return result.apply {
            posterUrl = movie.poster
            backgroundPosterUrl = movie.backdrop
            year = movie.year
            plot = movie.plot
            tags = movie.tags
            duration = movie.duration
            addActors(movie.actors)
        }
    }

    override suspend fun loadLinks(
        data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val sources = tryParseJson<List<MotChillEpisodeSource>>(data) ?: return false
        val seen = mutableSetOf<String>()
        val subtitles = mutableSetOf<String>()
        val emit: (ExtractorLink) -> Unit = { if (seen.add(it.url)) callback(it) }
        val emitSubtitle: (SubtitleFile) -> Unit = { if (subtitles.add(it.url)) subtitleCallback(it) }
        var lastError: Exception? = null
        for (source in sources.distinctBy { it.url }) {
            try {
                val doc = app.get(source.url).document
                for (player in MotChillParsing.players(doc)) {
                    try {
                        val display = "${source.server} · ${player.name}"
                        val direct = MotChillParsing.wrappedStream(player.url)
                            ?: player.url.takeIf { player.type in listOf("m3u8", "mp4") || MotChillParsing.isMedia(it) }
                        when {
                            direct != null -> emitMedia(direct, source.url, display, emit)
                            MotChillParsing.isVsmov(player.url) -> {
                                val embed = MotChillParsing.vsmov(app.get(player.url, referer = source.url).document)
                                embed.stream?.let { emitMedia(it, player.url, display, emit) }
                                embed.subtitles.forEach { emitSubtitle(SubtitleFile(it.language, it.url)) }
                            }
                            else -> loadExtractor(player.url, source.url, emitSubtitle, emit)
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        lastError = error // A failed host must not hide another working server.
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                lastError = error
            }
        }
        if (seen.isEmpty()) throw ErrorLoadingException("MotChill: no playable streams found${lastError?.message?.let { ": $it" }.orEmpty()}")
        return true
    }

    private suspend fun emitMedia(url: String, ref: String, display: String, callback: (ExtractorLink) -> Unit) {
        callback(newExtractorLink(name, display, url,
            if (url.substringBefore('?').endsWith(".mp4", true)) ExtractorLinkType.VIDEO else ExtractorLinkType.M3U8,
        ) { referer = ref })
    }
}
