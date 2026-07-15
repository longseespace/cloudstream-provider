package com.kkphimgenres

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class KKPhimPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(KKPhimProvider())
    }
}
