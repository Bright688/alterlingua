package com.alterlingua.app.keyboard

import com.alterlingua.app.notifications.AlterLinguaNotificationListener
import com.alterlingua.app.testing.MainDispatcherRule
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * Android builds a service with its plain constructor and only afterwards attaches the app to it. Anything in the
 * constructor that needs the app or a context therefore crashes the service, and the keyboard closes the moment it is
 * chosen. This once shipped (translation setup read the app object in a property initializer), so it is tested here.
 */
class ServiceConstructionTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun theKeyboardServiceCanBeConstructedBeforeItIsAttached() {
        assertNotNull(AlterLinguaKeyboardService())
    }

    @Test
    fun theNotificationListenerCanBeConstructedBeforeItIsAttached() {
        assertNotNull(AlterLinguaNotificationListener())
    }
}
