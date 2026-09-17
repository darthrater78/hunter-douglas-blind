package com.scrivtech.powerview.widget

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.pm.ShortcutManagerCompat

/**
 * The launcher-shortcut trampoline: starts one [com.scrivtech.powerview.data.ShadeAction]
 * and gets out of the way (build order step 11, spec §3.1).
 *
 * **Not exported, and it does not need to be.** A shortcut's intent is
 * started by the system under the *publishing* app's identity rather than the
 * launcher's — AOSP's `LauncherAppsService.startShortcutInner` says so in as
 * many words ("Note the target activity doesn't have to be exported"). So
 * this activity has no untrusted-input surface at all: nothing outside the
 * app can reach it, and the action id in the intent is one this app wrote.
 * Exporting it would have meant any installed app could move the shades.
 *
 * It finishes in `onCreate`, before anything is drawn. The work goes to
 * [CommandDispatch] like every other command surface, so a shortcut tap and a
 * widget tap are the same thing to everything downstream.
 */
public class RunActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val actionId = intent?.getStringExtra(EXTRA_ACTION_ID)

        if (actionId != null) {
            CommandDispatch.enqueue(applicationContext, actionId)

            // Tells the launcher this shortcut is used, which is what lets it
            // rank and surface it sensibly.
            ShortcutManagerCompat.reportShortcutUsed(this, actionId)

            // A shortcut has no surface of its own to report on, unlike a
            // widget button or a tile. Without this the tap is completely
            // silent, which is indistinguishable from a shortcut that did not
            // work. "Sending" is the honest tense: the outcome arrives
            // seconds later, on the widget or tile if one exists and in the
            // app either way.
            Toast.makeText(this, SHORTCUT_STARTED_TEXT, Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    public companion object {
        internal const val EXTRA_ACTION_ID: String = "com.scrivtech.powerview.widget.ACTION_ID"
    }
}
