package com.motchill

import com.lagradost.nicehttp.NiceResponse
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class MotChillHttpTest {
    private fun response(html: String, code: Int = 200) = NiceResponse(
        Response.Builder().request(Request.Builder().url("https://v9.streamvsmov.com/video/abc-123").build())
            .protocol(Protocol.HTTP_1_1).code(code).message("Test").body(html.toResponseBody()).build(),
        null,
    )

    @Test fun preservesResponseUrlWithActualNiceHttpParser() {
        val response = response("""<div id="rp-player" data-hash="abc-123"></div>
            <script>const options = {subtitles: [{"code":"vie","url":"/subtitle/vie.vtt"}], audios: [], enableSignedUrl: false};</script>
        """)
        // Reproduces the production failure that raw Jsoup tests did not cover.
        assertEquals("", response.document.baseUri())
        assertNull(MotChillParsing.vsmov(response.document).stream)
        val embed = MotChillParsing.vsmov(response.motChillDocument())
        assertEquals("https://v9.streamvsmov.com/stream/abc-123/master.m3u8", embed.stream)
        assertEquals("https://v9.streamvsmov.com/subtitle/vie.vtt", embed.subtitles.single().url)
    }

    @Test(expected = IOException::class) fun rejectsHttpErrorsInsteadOfParsingEmptyPlayers() {
        response("Unavailable", 503).motChillDocument()
    }
}
