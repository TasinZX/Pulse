package com.wolcompanion.app.service

import android.content.ClipboardManager
import android.content.Context
import android.service.quicksettings.TileService
import android.widget.Toast
import com.wolcompanion.app.PulseApp
import com.wolcompanion.app.core.remote.CommandClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings tile: one tap pushes the phone's clipboard text to the PC.
 * (Android 10+ restricts background clipboard reads — this works because a tile
 * click briefly counts as a foreground interaction.)
 */
class ClipboardTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val app = applicationContext as PulseApp
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()

        CoroutineScope(Dispatchers.IO).launch {
            val pc = app.settingsRepository.settings.first().pc
            val ok = !text.isNullOrEmpty() && pc.ip.isNotBlank() &&
                CommandClient(pc.ip).setClipboard(text)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    applicationContext,
                    if (ok) "Sent to PC" else "Couldn't send clipboard",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}
