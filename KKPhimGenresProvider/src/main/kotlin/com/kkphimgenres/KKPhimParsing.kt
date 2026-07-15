package com.kkphimgenres

import java.util.Locale

internal object KKPhimParsing {
    private val episodeNumberRegex = Regex("""\d+""")
    private val currentEpisodeRegex = Regex("""(?<=\()\d+(?=/\d+\))|\b\d+\b""")
    private const val defaultImageCdn = "https://phimimg.com"

    fun absoluteImageUrl(path: String?, cdn: String? = null): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        if (path.startsWith("//")) return "https:$path"

        val base = cdn?.takeIf { it.isNotBlank() } ?: defaultImageCdn
        return "${base.trimEnd('/')}/${path.trimStart('/')}"
    }

    fun episodeNumber(name: String?, slug: String?): Int? {
        val source = name?.takeIf { it.isNotBlank() } ?: slug ?: return null
        return episodeNumberRegex.find(source)?.value?.toIntOrNull()
    }

    fun episodeKey(episode: KKPhimEpisode): String {
        episodeNumber(episode.name, episode.slug)?.let { return "episode:$it" }

        return sequenceOf(episode.slug, episode.name)
            .filterNotNull()
            .map { it.trim().lowercase(Locale.ROOT) }
            .firstOrNull { it.isNotBlank() }
            ?: "unknown"
    }

    fun externalId(id: Any?): String? {
        return when (id) {
            null -> null
            is Number -> id.toLong().toString()
            else -> id.toString().trim().takeIf { it.isNotEmpty() && it != "null" }
        }
    }

    fun searchBadges(
        language: String?,
        episodeCurrent: String?,
        isSeries: Boolean,
    ): KKPhimSearchBadges {
        val isTrailer = episodeCurrent?.contains("Trailer", ignoreCase = true) == true
        val subbed = !isTrailer && language.containsAny("Vietsub", "Phụ đề")
        val dubbed = !isTrailer && language.containsAny("Thuyết Minh", "Lồng Tiếng")
        val episodeCount = if (isSeries && (subbed || dubbed)) {
            currentEpisodeRegex.find(episodeCurrent.orEmpty())?.value?.toIntOrNull()
        } else {
            null
        }

        return KKPhimSearchBadges(
            subbed = subbed,
            dubbed = dubbed,
            episodeCount = episodeCount,
        )
    }

    fun cardQuality(quality: String?): String? {
        return quality?.trim()?.takeIf { it.equals("CAM", ignoreCase = true) }?.uppercase()
    }

    private fun String?.containsAny(vararg values: String): Boolean {
        return this != null && values.any { contains(it, ignoreCase = true) }
    }
}

internal data class KKPhimSearchBadges(
    val subbed: Boolean,
    val dubbed: Boolean,
    val episodeCount: Int?,
)
