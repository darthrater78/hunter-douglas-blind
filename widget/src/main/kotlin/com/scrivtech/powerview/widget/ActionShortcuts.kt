package com.scrivtech.powerview.widget

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.scrivtech.powerview.data.ShadeAction

/**
 * Publishes one launcher shortcut per saved action, so a long-press on the
 * app icon runs one without opening it (build order step 11, spec §3.1).
 *
 * Republished whenever the action list changes rather than once at startup:
 * a shortcut is a copy of the label, so a renamed action would otherwise
 * carry its old name on the launcher until the next cold start. The *id* is
 * the action's id, which is what keeps a pinned shortcut pointing at the
 * right action across a rename.
 */
internal object ActionShortcuts {

    /**
     * Replaces the published set with the actions that deserve one.
     *
     * Failures are swallowed. This is a launcher decoration, called from an
     * application-scope collector with no user attached, and the system
     * throws here for reasons that are not the app's fault — a rate limit
     * during a burst of edits, or a launcher that does not support shortcuts
     * at all.
     */
    fun publish(context: Context, actions: List<ShadeAction>) {
        val max = runCatching {
            ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
        }.getOrDefault(MAX_SHORTCUTS)

        val shortcuts = shortcutActions(actions, max = minOf(max, MAX_SHORTCUTS))
            .map { action -> shortcutFor(context, action) }

        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun shortcutFor(context: Context, action: ShadeAction): ShortcutInfoCompat {
        // A shortcut intent must carry an action, or ShortcutInfoCompat
        // rejects it outright. ACTION_VIEW plus the explicit component is the
        // conventional filler; the extra is what actually selects the work.
        val intent = Intent(context, RunActionActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(RunActionActivity.EXTRA_ACTION_ID, action.id)

        return ShortcutInfoCompat.Builder(context, action.id)
            .setShortLabel(action.label)
            .setLongLabel(action.label)
            // TODO: a real icon, together with the launcher and tile icons.
            .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_menu_sort_by_size))
            .setIntent(intent)
            .build()
    }
}
