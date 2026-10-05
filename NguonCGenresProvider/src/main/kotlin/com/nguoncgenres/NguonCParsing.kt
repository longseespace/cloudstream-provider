package com.nguoncgenres

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.util.Locale

internal object NguonCParsing {
    val mapper = jacksonObjectMapper()
    const val BASE = "https://phim.nguonc.com"
    const val USER_AGENT = "Mozilla/5.0"

    // Public API docs list genre routes but no taxonomy-list endpoint.
    // These slugs are taken from the documentation site's genre navigation.
    val genres = linkedMapOf(
        "kinh-di" to "Kinh Dị", "hanh-dong" to "Hành Động", "bi-an" to "Bí Ẩn",
        "gay-can" to "Gây Cấn", "khoa-hoc-vien-tuong" to "Khoa Học Viễn Tưởng",
        "phieu-luu" to "Phiêu Lưu", "phim-hai" to "Hài", "hinh-su" to "Hình Sự",
        "chinh-kich" to "Chính Kịch", "gia-dinh" to "Gia Đình", "gia-tuong" to "Giả Tưởng",
        "hoat-hinh" to "Hoạt Hình", "lang-man" to "Lãng Mạn", "lich-su" to "Lịch Sử",
        "phim-nhac" to "Nhạc", "chien-tranh" to "Chiến Tranh", "tam-ly" to "Tâm Lý",
        "tinh-cam" to "Tình Cảm", "co-trang" to "Cổ Trang", "mien-tay" to "Miền Tây",
        "tai-lieu" to "Tài Liệu", "phim-18" to "Phim 18+",
    )

    fun absolute(value: String?, base: String = BASE): String? = runCatching {
        val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        URI(base).resolve(raw).takeIf { it.scheme in listOf("http", "https") && !it.host.isNullOrBlank() }?.toString()
    }.getOrNull()

    fun poster(movie: NguonCMovie): String? = sequenceOf(movie.thumbWebp, movie.thumb, movie.posterWebp, movie.poster)
        .mapNotNull { absolute(it, "https://img.nguonc.com/") }.firstOrNull()

    fun category(movie: NguonCMovie, group: String): List<String> = movie.category.orEmpty().values
        .filter { it.group?.name.equals(group, true) }.flatMap { it.list.orEmpty() }
        .mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }

    fun isAdult(movie: NguonCMovie) = category(movie, "Thể loại").any { it.contains("18+") || it.equals("Phim 18", true) }

    fun isSeries(movie: NguonCMovie): Boolean {
        val formats = category(movie, "Định dạng")
        if (formats.any { it.equals("Phim lẻ", true) }) return false
        if (formats.any { it.equals("Phim bộ", true) || it.contains("TV", true) }) return true
        when (movie.tmdb?.type) { "tv" -> return true; "movie" -> return false }
        return (movie.totalEpisodes ?: 0) > 1 ||
            Regex("(?i)tập|hoàn tất|hoàn thành").containsMatchIn(movie.currentEpisode.orEmpty()) ||
            movie.episodes.orEmpty().any { it.items.orEmpty().size > 1 }
    }

    fun currentCount(movie: NguonCMovie): Int? = if (isSeries(movie))
        Regex("\\d+").find(movie.currentEpisode.orEmpty())?.value?.toIntOrNull() else null
    fun subbed(movie: NguonCMovie) = !trailer(movie) &&
        listOf("Vietsub", "Phụ đề").any { movie.language.orEmpty().contains(it, true) }
    fun dubbed(movie: NguonCMovie) = !trailer(movie) &&
        listOf("Thuyết minh", "Lồng tiếng").any { movie.language.orEmpty().contains(it, true) }
    private fun trailer(movie: NguonCMovie) = movie.currentEpisode.orEmpty().contains("trailer", true)

    fun hasNext(response: NguonCList, requestedPage: Int): Boolean {
        val pagination = response.paginate
        return if (pagination?.totalPages != null) (pagination.currentPage ?: requestedPage) < pagination.totalPages
        else response.items.size >= (pagination?.pageSize?.takeIf { it > 0 } ?: 10)
    }

    fun slug(value: String): String {
        val candidate = value.substringBefore('?').trimEnd('/').substringAfterLast('/')
        require(Regex("[a-zA-Z0-9_-]+").matches(candidate)) { "Invalid NguonC movie slug" }
        return candidate
    }

    private fun number(episode: NguonCEpisode): String? = sequenceOf(episode.name, episode.slug)
        .filterNotNull().mapNotNull {
            Regex("(?i)^(?:(?:tập|tap|episode|ep)[\\s-]*)?(\\d+(?:\\.\\d+)?)$")
                .matchEntire(it.trim())?.groupValues?.get(1)
        }.firstOrNull()?.let { raw ->
            raw.toIntOrNull()?.toString() ?: raw
        }

    fun episodeKey(episode: NguonCEpisode): String? = number(episode)?.let { "episode:$it" }
        ?: sequenceOf(episode.slug, episode.name).filterNotNull().map { it.trim().lowercase(Locale.ROOT) }
            .firstOrNull(String::isNotEmpty)?.let { "special:$it" }

    fun episodes(movie: NguonCMovie): List<NguonCEpisodeGroup> {
        val groups = linkedMapOf<String, NguonCEpisodeGroup>()
        movie.episodes.orEmpty().forEach { server ->
            server.items.orEmpty().forEach episodeLoop@{ entry ->
                val key = episodeKey(entry) ?: return@episodeLoop
                val source = sequenceOf(entry.m3u8, entry.linkM3u8, entry.embed)
                    .mapNotNull { absolute(it) }.firstOrNull() ?: return@episodeLoop
                val old = groups[key]
                val sources = (old?.sources.orEmpty() + NguonCSource(server.name?.takeIf(String::isNotBlank) ?: "NguonC", source))
                    .distinctBy { it.server to it.url }
                groups[key] = NguonCEpisodeGroup(key, old?.name ?: entry.name?.takeIf(String::isNotBlank) ?: entry.slug.orEmpty(),
                    number(entry)?.toIntOrNull(), sources)
            }
        }
        return groups.values.sortedBy { it.key.removePrefix("episode:").toDoubleOrNull() ?: Double.MAX_VALUE }
    }

    fun isStreamC(url: String): Boolean = runCatching {
        val uri = URI(url)
        (uri.host == "streamc.xyz" || uri.host.endsWith(".streamc.xyz")) && uri.path == "/embed.php"
    }.getOrDefault(false)

    fun isMedia(url: String) = Regex("(?i)\\.(m3u8|mp4)(?:[?#]|$)").containsMatchIn(url)
}
