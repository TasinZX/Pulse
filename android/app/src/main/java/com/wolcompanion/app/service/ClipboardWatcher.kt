package com.wolcompanion.app.service

import android.content.ClipboardManager
import android.content.Context
import com.wolcompanion.app.PulseApp
import com.wolcompanion.app.core.remote.CommandClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Auto-clipboard-push: while Pulse is in the foreground, pushes newly copied text to
 * the PC (when the "Auto-send on copy" toggle is on).
 *
 * Android 10+ only delivers clipboard-change events to a *focused* app, so this is
 * inherently a foreground feature — the Automation screen documents that limit.
 */
class ClipboardWatcher(private val context: Context) {

    private val app = context.applicationContext as PulseApp
    private val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastText: String? = null

    private val listener = ClipboardManager.OnPrimaryClipChangedListener { onChange() }

    fun start() {
        runCatching { cm.addPrimaryClipChangedListener(listener) }
    }

    fun stop() {
        runCatching { cm.removePrimaryClipChangedListener(listener) }
    }

    /** Called when we pull the PC clipboard, so we don't immediately push it back. */
    fun markSynced(text: String) { lastText = text }

    private fun onChange() {
        scope.launch {
            if (!app.automationRepository.state.first().clipboard.autoPush) return@launch
            val text = cm.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(context)?.toString()
            if (text.isNullOrEmpty() || text == lastText) return@launch
            if (text.toByteArray().size > 1_000_000) return@launch  // 1 MB cap, text only
            lastText = text
            val pc = app.settingsRepository.settings.first().pc
            if (pc.ip.isNotBlank()) runCatching { CommandClient(pc.ip).setClipboard(text) }
        }
    }
}
