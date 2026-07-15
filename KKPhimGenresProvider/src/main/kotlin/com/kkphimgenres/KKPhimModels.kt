package com.kkphimgenres

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class KKPhimListResponse(
    // KKPhim uses boolean true for genre lists and the string "success" for search.
    val status: Any? = null,
    val msg: String? = null,
    val data: KKPhimListData? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class KKPhimListData(
    @JsonProperty("APP_DOMAIN_CDN_IMAGE")
    val imageCdn: String? = null,
    val items: List<KKPhimMovie> = emptyList(),
    val params: KKPhimListParams? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class KKPhimListParams(
    val pagination: KKPhimPagination? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class KKPhimPagination(
    val currentPage: Int? = null,
    val totalPages: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class KKPhimDetailResponse(
    val status: Boolean? = null,
    val msg: String? = null,
    val movie: KKPhimMovie? = null,
    val episodes: List<KKPhimServer> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class KKPhimMovie(
    val name: String? = null,
    val slug: String? = null,
    @JsonProperty("origin_name")
    val originName: String? = null,
    val content: String? = null,
    val type: String? = null,
    val status: String? = null,
    @JsonProperty("poster_url")
    val posterUrl: String? = null,
    @JsonProperty("thumb_url")
    val thumbUrl: String? = null,
    val time: String? = null,
    val quality: String? = null,
    val lang: String? = null,
    val year: Int? = null,
    val actor: List<String> = emptyList(),
    val category: List<KKPhimNamedValue> = emptyList(),
    val country: List<KKPhimNamedValue> = emptyList(),
    val tmdb: KKPhimExternalId? = null,
    val imdb: KKPhimExternalId? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class KKPhimNamedValue(
    val name: String? = null,
    val slug: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class KKPhimExternalId(
    val id: Any? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class KKPhimServer(
    @JsonProperty("server_name")
    val name: String? = null,
    @JsonProperty("server_data")
    val episodes: List<KKPhimEpisode> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class KKPhimEpisode(
    val name: String? = null,
    val slug: String? = null,
    @JsonProperty("link_m3u8")
    val m3u8Url: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class KKPhimStream(
    val server: String,
    val url: String,
    val quality: String? = null,
)
