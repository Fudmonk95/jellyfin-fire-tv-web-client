package org.jellyfin.mobile.webapp

import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import org.jellyfin.mobile.utils.isTelevision
import android.webkit.WebView

/** Remote navigation bridge; native player retains its own key handling. */
class TvRemoteController(private val webView: WebView) {
    // Snackbars share the Activity window with WebView, unlike AlertDialogs.
    // Give their native action focus once, then restore the previous view on dismissal.
    private var actionView: View? = null
    private var previousFocus: View? = null
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        val action = webView.rootView.findViewById<View>(com.google.android.material.R.id.snackbar_action)
            ?.takeIf { it.isShown && it.isEnabled && it.width > 0 }
        if (action !== actionView) {
            val oldAction = actionView
            actionView = action
            if (action != null && webView.isShown && webView.hasWindowFocus()) {
                previousFocus = webView.rootView.findFocus()
                action.isFocusableInTouchMode = true
                action.requestFocus()
            } else if (action == null && oldAction != null) {
                val target = previousFocus?.takeIf { it.isShown } ?: webView
                if (target.isShown && webView.hasWindowFocus()) target.requestFocus()
                previousFocus = null
            }
        }
    }

    fun attach() {
        if (webView.context.isTelevision) {
            webView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
            webView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    view.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
                    view.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
                }
                override fun onViewDetachedFromWindow(view: View) {
                    view.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
                    actionView = null
                    previousFocus = null
                }
            })
        }
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
