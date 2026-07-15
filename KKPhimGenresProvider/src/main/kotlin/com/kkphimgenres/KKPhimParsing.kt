package com.kkphimgenres

import java.util.Locale

internal object KKPhimParsing {
    private val episodeNumberRegex = Regex("""\d+""")
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
}
