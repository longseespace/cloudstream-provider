package com.kkphimgenres

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.getQualityFromString
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup

class KKPhimProvider : MainAPI() {
    override var mainUrl = "https://phimapi.com"
    override var name = "KKPhim Genres"
    override var lang = "vi"

    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val regularMainPage = mainPageOf(
        "kinh-di" to "Kinh Dị",
        "hanh-dong" to "Hành Động",
        "bi-an" to "Bí Ẩn",
        "hinh-su" to "Hình Sự",
        "vien-tuong" to "Viễn Tưởng",
        "phieu-luu" to "Phiêu Lưu",
        "hai-huoc" to "Hài Hước",
        "tinh-cam" to "Tình Cảm",
        "tam-ly" to "Tâm Lý",
        "chinh-kich" to "Chính Kịch",
        "vo-thuat" to "Võ Thuật",
        "co-trang" to "Cổ Trang",
        "than-thoai" to "Thần Thoại",
        "chien-tranh" to "Chiến Tranh",
        "khoa-hoc" to "Khoa Học",
        "kinh-dien" to "Kinh Điển",
        "lich-su" to "Lịch Sử",
        "mien-tay" to "Miền Tây",
        "gia-dinh" to "Gia Đình",
        "hoc-duong" to "Học Đường",
        "tre-em" to "Trẻ Em",
        "the-thao" to "Thể Thao",
        "tai-lieu" to "Tài Liệu",
        "am-nhac" to "Âm Nhạc",
        "phim-ngan" to "Phim Ngắn",
    )

    private val adultMainPage = mainPageOf(ADULT_GENRE_SLUG to "Phim 18+")

    override val mainPage: List<MainPageData>
        get() = if (adultContentEnabled) regularMainPage + adultMainPage else regularMainPage

    private val adultContentEnabled: Boolean
        get() = MainAPI.settingsForProvider.enableAdult

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (!adultContentEnabled && request.data == ADULT_GENRE_SLUG) {
            return newHomePageResponse(request, emptyList<SearchResponse>(), false)
        }

        val response = app.get(
            "$mainUrl/v1/api/the-loai/${request.data}",
            params = mapOf("page" to page.toString(), "limit" to PAGE_SIZE.toString()),
        ).parsedSafe<KKPhimListResponse>()
            ?: throw ErrorLoadingException("KKPhim returned invalid genre JSON")

        val data = response.data
            ?: throw ErrorLoadingException(response.msg ?: "KKPhim returned no genre data")
        val includeAdultContent = adultContentEnabled
        val results = data.items
            .asSequence()
            .filter { includeAdultContent || !KKPhimParsing.isAdultContent(it.category) }
            .mapNotNull { it.toSearchResponse(data.imageCdn) }
            .toList()
        val pagination = data.params?.pagination
        val hasNext = when {
            pagination?.currentPage != null && pagination.totalPages != null ->
                pagination.currentPage < pagination.totalPages
            else -> data.items.size >= PAGE_SIZE
        }

        return newHomePageResponse(request, results, hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()

        val response = app.get(
            "$mainUrl/v1/api/tim-kiem",
            params = mapOf("keyword" to query.trim(), "limit" to SEARCH_LIMIT.toString()),
        ).parsedSafe<KKPhimListResponse>() ?: return emptyList()

        val data = response.data ?: return emptyList()
        val includeAdultContent = adultContentEnabled
        return data.items
            .asSequence()
            .filter { includeAdultContent || !KKPhimParsing.isAdultContent(it.category) }
            .mapNotNull { it.toSearchResponse(data.imageCdn) }
            .toList()
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url).parsedSafe<KKPhimDetailResponse>()
            ?: throw ErrorLoadingException("KKPhim returned invalid detail JSON")
        val movie = response.movie
            ?: throw ErrorLoadingException(response.msg ?: "KKPhim returned no movie data")
        if (!adultContentEnabled && KKPhimParsing.isAdultContent(movie.category)) {
            throw ErrorLoadingException("Adult content is disabled in CloudStream settings")
        }
        val title = movie.name?.takeIf { it.isNotBlank() }
            ?: throw ErrorLoadingException("KKPhim movie title is missing")
        val type = movie.cloudstreamType()

        return if (type == TvType.Movie) {
            val streams = response.episodes.flatMap { server ->
                server.episodes.mapNotNull { episode -> episode.toStream(server, movie.quality) }
            }.distinctBy { it.url }

            newMovieLoadResponse(title, url, type, streams) {
                applyKKPhimMetadata(movie)
            }
        } else {
            val episodes = buildEpisodeGroups(response.episodes, movie.quality).map { group ->
                newEpisode(group.streams) {
                    name = group.name
                    season = 1
                    episode = group.number
                }
            }

            newTvSeriesLoadResponse(title, url, type, episodes) {
                applyKKPhimMetadata(movie)
                showStatus = when (movie.status?.lowercase()) {
                    "completed" -> ShowStatus.Completed
                    "ongoing" -> ShowStatus.Ongoing
                    else -> null
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val streams = tryParseJson<List<KKPhimStream>>(data)
            ?: tryParseJson<KKPhimStream>(data)?.let(::listOf)
            ?: return false

        var emitted = false
        streams.distinctBy { it.url }.forEach { stream ->
            callback(
                newExtractorLink(
                    source = name,
                    name = stream.server,
                    url = stream.url,
                    type = ExtractorLinkType.M3U8,
                ) {
                    referer = PLAYBACK_REFERER
                    quality = getQualityFromName(stream.quality)
                },
            )
            emitted = true
        }

        return emitted
    }

    private fun KKPhimMovie.toSearchResponse(imageCdn: String?): SearchResponse? {
        val title = name?.takeIf { it.isNotBlank() } ?: return null
        val movieSlug = slug?.takeIf { it.isNotBlank() } ?: return null
        val resultType = cloudstreamType()
        val badges = KKPhimParsing.searchBadges(
            language = lang,
            episodeCurrent = episodeCurrent,
            isSeries = resultType == TvType.TvSeries,
        )

        return newAnimeSearchResponse(title, "$mainUrl/phim/$movieSlug", resultType) {
            posterUrl = KKPhimParsing.absoluteImageUrl(
                this@toSearchResponse.posterUrl ?: thumbUrl,
                imageCdn,
            )
            year = this@toSearchResponse.year
            quality = KKPhimParsing.cardQuality(this@toSearchResponse.quality)
                ?.let { getQualityFromString(it) }
            addDubStatus(
                dubExist = badges.dubbed,
                subExist = badges.subbed,
                dubEpisodes = badges.episodeCount,
                subEpisodes = badges.episodeCount,
            )
        }
    }

    private fun KKPhimMovie.cloudstreamType(): TvType {
        return if (type.equals("single", ignoreCase = true)) TvType.Movie else TvType.TvSeries
    }

    private fun com.lagradost.cloudstream3.LoadResponse.applyKKPhimMetadata(movie: KKPhimMovie) {
        posterUrl = KKPhimParsing.absoluteImageUrl(movie.posterUrl)
        backgroundPosterUrl = KKPhimParsing.absoluteImageUrl(movie.thumbUrl)
        year = movie.year
        plot = Jsoup.parse(movie.content.orEmpty()).text().takeIf { it.isNotBlank() }
        tags = movie.category.mapNotNull { it.name?.takeIf(String::isNotBlank) }
        duration = Regex("""\d+""").find(movie.time.orEmpty())?.value?.toIntOrNull()
        addActors(movie.actor)
        addTMDbId(KKPhimParsing.externalId(movie.tmdb?.id))
        addImdbId(KKPhimParsing.externalId(movie.imdb?.id))
    }

    private fun KKPhimEpisode.toStream(
        server: KKPhimServer,
        quality: String?,
    ): KKPhimStream? {
        val streamUrl = m3u8Url?.takeIf { it.startsWith("http") } ?: return null
        return KKPhimStream(
            server = server.name?.takeIf { it.isNotBlank() } ?: "KKPhim",
            url = streamUrl,
            quality = quality,
        )
    }

    private fun buildEpisodeGroups(
        servers: List<KKPhimServer>,
        quality: String?,
    ): List<EpisodeGroup> {
        val groups = linkedMapOf<String, MutableEpisodeGroup>()

        servers.forEach { server ->
            server.episodes.forEach { episode ->
                val stream = episode.toStream(server, quality) ?: return@forEach
                val key = KKPhimParsing.episodeKey(episode)
                val group = groups.getOrPut(key) {
                    MutableEpisodeGroup(
                        name = episode.name?.takeIf { it.isNotBlank() } ?: "Tập phim",
                        number = KKPhimParsing.episodeNumber(episode.name, episode.slug),
                    )
                }
                if (group.streams.none { it.url == stream.url }) group.streams += stream
            }
        }

        return groups.values.map {
            EpisodeGroup(name = it.name, number = it.number, streams = it.streams.toList())
        }
    }

    private data class MutableEpisodeGroup(
        val name: String,
        val number: Int?,
        val streams: MutableList<KKPhimStream> = mutableListOf(),
    )

    private data class EpisodeGroup(
        val name: String,
        val number: Int?,
        val streams: List<KKPhimStream>,
    )

    private companion object {
        const val PAGE_SIZE = 20
        const val SEARCH_LIMIT = 50
        const val PLAYBACK_REFERER = "https://kkphim.com/"
        const val ADULT_GENRE_SLUG = "phim-18"
    }
}
