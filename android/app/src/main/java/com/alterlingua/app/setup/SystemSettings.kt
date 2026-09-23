package com.alterlingua.app.setup

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.alterlingua.app.notifications.AlterLinguaNotificationListener

/** The Android system screens the setup steps send the user to. */
object SystemSettings {
    /** Android's list of keyboards, where the user switches AlterLingua on. */
    fun keyboardList(): Intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)

    /**
     * The notification-access page for AlterLingua itself (Android 11+), or the general list on older phones.
     */
    fun notificationAccess(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, AlterLinguaNotificationListener::class.java).flattenToString(),
            )
        } else {
            notificationAccessList()
        }

    /** Android's list of apps with notification access. */
    fun notificationAccessList(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /** AlterLingua's notification settings (channels, and the on/off switch for all its notifications). */
    fun appNotificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** AlterLingua's own page in Android settings (permissions, "Allow restricted settings" menu). */
    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
}

/**
 * Opens a system settings screen. Some phones lack a specific screen, so if [primary] cannot be opened,
 * [fallback] is tried. Returns false if neither could be opened.
 */
fun Context.openSettings(primary: Intent, fallback: Intent? = null): Boolean {
    for (intent in listOfNotNull(primary, fallback)) {
        try {
            startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
            // try the next one
        } catch (_: SecurityException) {
            // try the next one
        }
    }
    return false
}
