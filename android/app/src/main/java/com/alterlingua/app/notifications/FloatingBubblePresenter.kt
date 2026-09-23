package com.alterlingua.app.notifications

import android.app.PendingIntent
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.alterlingua.app.R
import com.alterlingua.app.keyboard.KeyboardColors
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages

/**
 * Shows the latest translated incoming message as a small floating bubble on top of whatever app the user is in, for
 * a few seconds. Fed entirely by the same data the notification is built from (see [IncomingTranslator]); this never
 * reads another app's screen — no `AccessibilityService` is used anywhere in AlterLingua (CLAUDE.md section 39).
 *
 * Off by default ([UserSettings.floatingTranslationEnabled][com.alterlingua.app.learning.UserSettings]) and inert
 * without Android's "Display over other apps" permission, which [canPost] checks before ever touching WindowManager,
 * exactly like [TranslatedNotificationPresenter] checks notification access before posting.
 */
class FloatingBubblePresenter(
    context: Context,
    private val enabled: () -> Boolean,
    private val appLanguage: () -> Language? = { null },
    private val autoDismissMillis: Long = 8_000,
) : TranslationPresenter {

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { removeShownView() }

    private var shownView: View? = null
    private var shownKey: String? = null

    override fun canPost(): Boolean = enabled() && Settings.canDrawOverlays(appContext)

    override fun show(conversation: TranslatedConversation) {
        if (!canPost()) return
        main.post { showOnMainThread(conversation) }
    }

    override fun remove(key: String) {
        main.post { if (shownKey == key) removeShownView() }
    }

    private fun showOnMainThread(conversation: TranslatedConversation) {
        if (!canPost()) return // the permission or the setting may have changed since show() was called
        removeShownView()

        val localized = com.alterlingua.app.localization.AppLanguage.wrap(appContext, appLanguage())
        val latest = conversation.lines.last()
        val from = Languages.fromCode(latest.sourceLanguage)?.displayName ?: latest.sourceLanguage.uppercase()
        val colors = KeyboardColors.forContext(localized)
        val density = localized.resources.displayMetrics.density

        val card = LinearLayout(localized).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 14 * density
                setColor(colors.board)
            }
            elevation = 8 * density
            addView(
                TextView(localized).apply {
                    text = if (conversation.isGroup && latest.sender.isNotBlank()) "${latest.sender}: ${latest.text}" else latest.text
                    setTextColor(colors.text)
                    textSize = 15f
                    maxLines = 4
                },
            )
            addView(
                TextView(localized).apply {
                    text = localized.getString(R.string.notif_translated_from, from)
                    setTextColor(colors.accent)
                    textSize = 11f
                    setPadding(0, (4 * density).toInt(), 0, 0)
                },
            )
            (conversation.openIntent as? PendingIntent)?.let { intent ->
                setOnClickListener {
                    runCatching { intent.send() }
                    removeShownView()
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (90 * density).toInt()
        }

        try {
            windowManager.addView(card, params)
        } catch (_: Exception) {
            // The permission was revoked, or the window could not be added right now: fail silently, exactly like a
            // notification that canPost() said no to. Nothing the user typed or received is affected either way.
            return
        }
        shownView = card
        shownKey = conversation.key
        main.postDelayed(dismissRunnable, autoDismissMillis)
    }

    private fun removeShownView() {
        main.removeCallbacks(dismissRunnable)
        shownView?.let { view -> runCatching { windowManager.removeView(view) } }
        shownView = null
        shownKey = null
    }
}
