package com.useconatus.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.useconatus.app.ui.AppLink

/** Opens quick add over whatever is on screen, in a task of its own. */
fun quickAddIntent(context: Context): Intent =
    Intent(context, QuickAddActivity::class.java).setAction(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/**
 * The launcher icon's long-press shortcuts. They're published from code because a shortcut names
 * the app's package, and the debug and preview builds each have their own.
 */
fun publishShortcuts(context: Context) {
    if (ShortcutManagerCompat.getDynamicShortcuts(context).size == 4) return
    fun shortcut(id: String, label: String, icon: Int, intent: Intent) =
        ShortcutInfoCompat.Builder(context, id).setShortLabel(label).setIcon(IconCompat.createWithResource(context, icon)).setIntent(intent).build()
    ShortcutManagerCompat.setDynamicShortcuts(
        context,
        listOf(
            shortcut("add", "Add task", R.drawable.ic_add, quickAddIntent(context)),
            shortcut("today", "Today", R.drawable.ic_today, AppLink.intent(context, AppLink.Today)),
            shortcut("upcoming", "Upcoming", R.drawable.ic_upcoming, AppLink.intent(context, AppLink.Upcoming)),
            shortcut("search", "Search", R.drawable.ic_search, AppLink.intent(context, AppLink.Search)),
        ),
    )
}

/** A Quick Settings tile that opens quick add, unlocking first when the phone is locked. */
class QuickAddTile : TileService() {
    override fun onStartListening() {
        qsTile?.run {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() = if (isLocked) unlockAndRun(::open) else open()

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun open() {
        val intent = quickAddIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
