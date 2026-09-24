package com.alterlingua.app.setup

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.alterlingua.app.accessibility.AlterLinguaAccessibilityService
import com.alterlingua.app.keyboard.AlterLinguaKeyboardService
import com.alterlingua.app.notifications.AlterLinguaNotificationListener

/** Reads what is set up on this phone. Nothing here changes anything. */
interface SetupChecker {
    /** AlterLingua is switched on in Android's keyboard list. */
    fun keyboardEnabled(): Boolean

    /** AlterLingua is the keyboard currently in use. */
    fun keyboardSelected(): Boolean

    /** The user allowed notification access for AlterLingua. */
    fun notificationAccessGranted(): Boolean

    /** The microphone permission is granted. */
    fun microphoneGranted(): Boolean

    /** AlterLingua is allowed to post notifications. */
    fun postNotificationsAllowed(): Boolean

    /** AlterLingua is allowed to draw over other apps (needed for the floating translation bubble). */
    fun overlayPermissionGranted(): Boolean

    /** The user has switched on AlterLinguaAccessibilityService in Android's Accessibility settings. */
    fun accessibilityServiceEnabled(): Boolean
}

class AndroidSetupChecker(context: Context) : SetupChecker {
    private val appContext = context.applicationContext
    private val keyboardClassName = AlterLinguaKeyboardService::class.java.name
    private val listener = ComponentName(appContext, AlterLinguaNotificationListener::class.java)

    override fun keyboardEnabled(): Boolean {
        val manager = appContext.getSystemService(InputMethodManager::class.java) ?: return false
        // The enabled-keyboards setting cannot be read directly by apps targeting Android 14+, so ask the manager.
        return manager.enabledInputMethodList.any {
            it.packageName == appContext.packageName && it.serviceName == keyboardClassName
        }
    }

    override fun keyboardSelected(): Boolean {
        val id = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return isSelectedKeyboard(id, appContext.packageName, keyboardClassName)
    }

    override fun notificationAccessGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            appContext.getSystemService(NotificationManager::class.java)?.isNotificationListenerAccessGranted(listener) == true
        } else {
            NotificationManagerCompat.getEnabledListenerPackages(appContext).contains(appContext.packageName)
        }

    override fun postNotificationsAllowed(): Boolean = NotificationManagerCompat.from(appContext).areNotificationsEnabled()

    override fun microphoneGranted(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun overlayPermissionGranted(): Boolean = Settings.canDrawOverlays(appContext)

    override fun accessibilityServiceEnabled(): Boolean {
        val manager = appContext.getSystemService(AccessibilityManager::class.java) ?: return false
        val target = ComponentName(appContext, AlterLinguaAccessibilityService::class.java)
        return manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info -> info.resolveInfo?.serviceInfo?.let { ComponentName(it.packageName, it.name) } == target }
    }
}
