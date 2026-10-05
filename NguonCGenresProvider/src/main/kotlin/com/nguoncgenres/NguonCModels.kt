package com.nguoncgenres

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class NguonCList(
    val status: String? = null,
    val message: String? = null,
    val items: List<NguonCMovie> = emptyList(),
    val paginate: NguonCPagination? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class NguonCPagination(
    @JsonProperty("current_page") val currentPage: Int? = null,
    @JsonProperty("total_page") val totalPages: Int? = null,
    @JsonProperty("items_per_page") val pageSize: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class NguonCDetail(
    val status: String? = null,
    val message: String? = null,
    val movie: NguonCMovie? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class NguonCMovie(
    val name: String? = null,
    val slug: String? = null,
    val year: Int? = null,
    val description: String? = null,
    val quality: String? = null,
    val language: String? = null,
    val time: String? = null,
    val casts: String? = null,
    @JsonProperty("current_episode") val currentEpisode: String? = null,
    @JsonProperty("total_episodes") val totalEpisodes: Int? = null,
    @JsonProperty("thumb_url") val thumb: String? = null,
    @JsonProperty("poster_url") val poster: String? = null,
    @JsonProperty("thumb_url_webp") val thumbWebp: String? = null,
    @JsonProperty("poster_url_webp") val posterWebp: String? = null,
    val category: Map<String, NguonCCategory>? = null,
    val tmdb: NguonCExternalId? = null,
    val imdb: NguonCExternalId? = null,
    val episodes: List<NguonCServer>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class NguonCCategory(val group: NguonCNamed? = null, val list: List<NguonCNamed>? = null)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class NguonCNamed(val name: String? = null)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class NguonCExternalId(val id: String? = null, val type: String? = null, val season: Int? = null)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class NguonCServer(
    @JsonProperty("server_name") val name: String? = null,
    val items: List<NguonCEpisode>? = null,
)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class NguonCEpisode(
    val name: String? = null,
    val slug: String? = null,
    val embed: String? = null,
    val m3u8: String? = null,
    @JsonProperty("link_m3u8") val linkM3u8: String? = null,
)

internal data class NguonCSource(val server: String, val url: String)
internal data class NguonCEpisodeGroup(
    val key: String, val name: String, val number: Int?, val sources: List<NguonCSource>,
)
// Store identity, never temporary CDN grants, in CloudStream's episode cache.
internal data class NguonCPlayback(val slug: String = "", val episodeKey: String? = null)
