package com.najishab.aether.core

import android.content.Context
import com.najishab.aether.BuildConfig
import com.mixpanel.android.mpmetrics.MixpanelAPI
import org.json.JSONObject

/**
 * Thin wrapper around Mixpanel so call sites don't touch the SDK directly.
 * init() must run once before any log*() call - see AetherApp.
 */
object AetherAnalytics {
    private val MIXPANEL_TOKEN = BuildConfig.MIXPANEL_TOKEN

    private var mixpanel: MixpanelAPI? = null

    fun init(context: Context) {
        mixpanel = MixpanelAPI.getInstance(context.applicationContext, MIXPANEL_TOKEN, true)
    }

    fun logConnect(protocol: String) {
        mixpanel?.track("vpn_connect", JSONObject().put("protocol", protocol))
    }

    fun logDisconnect() {
        mixpanel?.track("vpn_disconnect")
    }

    fun logConnectFailed(reason: String) {
        mixpanel?.track("vpn_connect_failed", JSONObject().put("reason", reason))
    }
}
