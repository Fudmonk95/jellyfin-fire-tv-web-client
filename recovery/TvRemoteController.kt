package org.jellyfin.mobile.webapp

import android.view.KeyEvent
import android.webkit.WebView

/** Remote navigation bridge; native player retains its own key handling. */
class TvRemoteController(private val webView: WebView) {
    fun attach() {
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.setOnKeyListener { _, keyCode, event ->
            val command = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> "up"
                KeyEvent.KEYCODE_DPAD_DOWN -> "down"
                KeyEvent.KEYCODE_DPAD_LEFT -> "left"
                KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
                KeyEvent.KEYCODE_DPAD_CENTER -> "select"
                else -> return@setOnKeyListener false
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                webView.evaluateJavascript("window.JellyfinWebTv && window.JellyfinWebTv.handleKey('$command')", null)
            }
            true
        }
        webView.requestFocus()
    }
}
