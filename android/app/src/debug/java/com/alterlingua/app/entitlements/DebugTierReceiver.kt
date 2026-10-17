package com.alterlingua.app.entitlements

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alterlingua.app.AlterLinguaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * DEBUG BUILDS ONLY. Lets `adb` set the subscription tier directly, so Standard/Premium-gated features can be
 * tried on a real device before real Google Play Billing products exist (see docs/entitlements.md):
 *
 *   adb shell am broadcast -n com.alterlingua.app/.entitlements.DebugTierReceiver \
 *     -a com.alterlingua.app.DEBUG_SET_TIER --es tier PREMIUM
 *
 * [tier] is one of FREE, STANDARD, PREMIUM. Release builds contain no such receiver.
 */
class DebugTierReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tier = intent.getStringExtra("tier")?.let { runCatching { Tier.valueOf(it) }.getOrNull() } ?: return
        val app = context.applicationContext as AlterLinguaApplication
        CoroutineScope(Dispatchers.Default).launch { app.userSettings.update { it.copy(tier = tier) } }
    }
}
