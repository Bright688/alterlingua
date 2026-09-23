package com.alterlingua.app.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.alterlingua.app.AlterLinguaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Listens to notifications so incoming WhatsApp messages can be translated (CLAUDE.md sections 21 and 38).
 *
 * Only notifications from accepted sources (WhatsApp) are ever read; every other app's notification is dropped on
 * its package name alone, before any of its content is looked at. What is read is only what Android exposes to a
 * notification listener. Nothing is stored, and the WhatsApp conversation itself is never touched.
 */
class AlterLinguaNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val translator get() = (application as AlterLinguaApplication).incomingTranslator

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        if (!IncomingSources.accepts(posted.packageName)) return
        val snapshot = NotificationSnapshotReader.read(posted)
        scope.launch { translator.handle(snapshot) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val removed = sbn ?: return
        if (!IncomingSources.accepts(removed.packageName)) return
        scope.launch { translator.onRemoved(removed.key) }
    }

    /** Access was switched off: forget everything held in memory. */
    override fun onListenerDisconnected() {
        scope.launch { translator.clear() }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
