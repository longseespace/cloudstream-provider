package com.motchill

import com.lagradost.nicehttp.NiceResponse
import org.jsoup.nodes.Document
import java.io.IOException

/** NiceHttp's document parser drops the response URL, breaking relative player paths. */
internal fun NiceResponse.motChillDocument(): Document {
    if (!isSuccessful) throw IOException("MotChill HTTP $code from ${java.net.URI(url).host}")
    return document.apply { setBaseUri(url) }
}
