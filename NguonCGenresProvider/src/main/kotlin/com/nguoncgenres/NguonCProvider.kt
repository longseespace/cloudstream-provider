package com.nguoncgenres

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.util.concurrent.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup

class NguonCProvider : MainAPI() {
    override var mainUrl = NguonCParsing.BASE
    override var name = "NguonC Genres"
    override var lang = "vi"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val api = NguonCApi { url, body, headers ->
        val response = if (body == null) app.get(url, headers = headers)
            else app.post(url, headers = headers, requestBody = body.toRequestBody("application/json".toMediaType()))
        if (response.code !in 200..299) throw ErrorLoadingException("NguonC server returned HTTP ${response.code}")
        response.text
    }

    override val mainPage: List<MainPageData>
        get() = mainPageOf("/films/phim-moi-cap-nhat" to "Mới Cập Nhật") +
            NguonCParsing.genres.filter { it.key != "phim-18" || settingsForProvider.enableAdult }
                .flatMap { (slug, title) -> mainPageOf("/films/the-loai/$slug" to title) }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (request.data.endsWith("/phim-18") && !settingsForProvider.enableAdult)
            return newHomePageResponse(request, emptyList<SearchResponse>(), false)
        val result = api.list(request.data, page)
        return newHomePageResponse(request, result.items.filterAllowed().mapNotNull { it.card() }, NguonCParsing.hasNext(result, page))
    }

    override suspend fun search(query: String): List<SearchResponse> = if (query.isBlank()) emptyList()
        else api.search(query).items.filterAllowed().mapNotNull { it.card() }

    private fun List<NguonCMovie>.filterAllowed() = filter { settingsForProvider.enableAdult || !NguonCParsing.isAdult(it) }

    private fun NguonCMovie.card(): SearchResponse? {
        val title = name?.takeIf(String::isNotBlank) ?: return null
        val slug = slug?.takeIf(String::isNotBlank) ?: return null
        return newAnimeSearchResponse(title, "$mainUrl/api/film/$slug", if (NguonCParsing.isSeries(this)) TvType.TvSeries else TvType.Movie) {
            posterUrl = NguonCParsing.poster(this@card)
            year = this@card.year
            quality = this@card.quality?.takeIf { it.contains("CAM", true) }?.let { getQualityFromString("CAM") }
            addDubStatus(dubExist = NguonCParsing.dubbed(this@card), subExist = NguonCParsing.subbed(this@card),
                dubEpisodes = NguonCParsing.currentCount(this@card), subEpisodes = NguonCParsing.currentCount(this@card))
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val slug = NguonCParsing.slug(url)
        val movie = api.detail(slug)
        checkAllowed(movie)
        val title = movie.name?.takeIf(String::isNotBlank) ?: throw ErrorLoadingException("NguonC title is missing")
        val response = if (NguonCParsing.isSeries(movie)) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, NguonCParsing.episodes(movie).map { group ->
                newEpisode(NguonCPlayback(slug, group.key)) {
                    name = group.name
                    episode = group.number
                    season = movie.tmdb?.season ?: 1
                }
            }) {
                showStatus = when {
                    movie.currentEpisode.orEmpty().contains("Hoàn", true) -> ShowStatus.Completed
                    movie.currentEpisode.orEmpty().contains("Tập", true) -> ShowStatus.Ongoing
                    else -> null
                }
            }
        } else newMovieLoadResponse(title, url, TvType.Movie, NguonCPlayback(slug))
        return response.apply {
            posterUrl = NguonCParsing.poster(movie)
            backgroundPosterUrl = sequenceOf(movie.posterWebp, movie.poster).mapNotNull { NguonCParsing.absolute(it) }.firstOrNull()
            year = movie.year
            plot = Jsoup.parse(movie.description.orEmpty()).text().takeIf(String::isNotBlank)
            tags = NguonCParsing.category(movie, "Thể loại")
            duration = Regex("\\d+").find(movie.time.orEmpty())?.value?.toIntOrNull()
            addActors(movie.casts.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty))
            addTMDbId(movie.tmdb?.id)
            addImdbId(movie.imdb?.id)
        }
    }

    private fun checkAllowed(movie: NguonCMovie) {
        if (!settingsForProvider.enableAdult && NguonCParsing.isAdult(movie))
            throw ErrorLoadingException("Adult content is disabled in CloudStream settings")
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val payload = tryParseJson<NguonCPlayback>(data) ?: return false
        val movie = api.detail(payload.slug)
        checkAllowed(movie)
        val sources = NguonCParsing.episodes(movie).filter { payload.episodeKey == null || it.key == payload.episodeKey }
            .flatMap { it.sources }.distinctBy { it.url }
        val seen = mutableSetOf<String>()
        var lastError: String? = null
        for (source in sources) {
            try {
                val direct = when {
                    NguonCParsing.isMedia(source.url) -> source.url
                    NguonCParsing.isStreamC(source.url) -> api.streamC(source.url)
                    else -> null
                }
                if (direct != null && seen.add(direct)) {
                    callback(newExtractorLink(name, source.server, direct,
                        if (direct.substringBefore('?').endsWith(".mp4", true)) ExtractorLinkType.VIDEO else ExtractorLinkType.M3U8,
                    ) {
                        referer = if (NguonCParsing.isStreamC(source.url)) source.url else "$mainUrl/"
                        headers = mapOf("User-Agent" to NguonCParsing.USER_AGENT)
                        quality = getQualityFromName(movie.quality)
                    })
                } else if (direct == null) {
                    loadExtractor(source.url, "$mainUrl/", subtitleCallback) { link ->
                        if (seen.add(link.url)) callback(link)
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                lastError = error.message
            }
        }
        if (seen.isEmpty()) throw ErrorLoadingException("NguonC: no playable streams${lastError?.let { ": $it" }.orEmpty()}")
        return true
    }
}
