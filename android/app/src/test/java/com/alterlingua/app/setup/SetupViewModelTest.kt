package com.alterlingua.app.setup

import com.alterlingua.app.testing.FakeUserSettingsRepository
import com.alterlingua.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeChecker : SetupChecker {
    var enabled = false
    var selected = false
    var notifications = false
    var microphone = false
    var postNotifications = false
    var overlayPermission = false
    var accessibilityService = false
    override fun keyboardEnabled() = enabled
    override fun keyboardSelected() = selected
    override fun notificationAccessGranted() = notifications
    override fun microphoneGranted() = microphone
    override fun postNotificationsAllowed() = postNotifications
    override fun overlayPermissionGranted() = overlayPermission
    override fun accessibilityServiceEnabled() = accessibilityService
}

class SetupViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val checker = FakeChecker()
    private val repo = FakeUserSettingsRepository()

    private fun viewModel() = SetupViewModel(checker, repo)

    @Test
    fun freshInstall_showsNothingSetUp() {
        val status = viewModel().status.value
        assertEquals(SetupStatus(), status)
        assertEquals(MicrophoneStatus.NOT_ASKED, status.microphone)
    }

    @Test
    fun refresh_picksUpChangesMadeInAndroidSettings() {
        val vm = viewModel()

        checker.enabled = true
        vm.refresh(showMicrophoneRationale = false)
        assertTrue(vm.status.value.keyboardEnabled)
        assertFalse(vm.status.value.keyboardSelected)

        checker.selected = true
        checker.notifications = true
        vm.refresh(showMicrophoneRationale = false)
        assertTrue(vm.status.value.keyboardReady)
        assertTrue(vm.status.value.notificationAccess)

        // The user switches the keyboard off again in Android settings.
        checker.enabled = false
        checker.selected = false
        vm.refresh(showMicrophoneRationale = false)
        assertFalse(vm.status.value.keyboardEnabled)
    }

    @Test
    fun microphone_goesFromNotAsked_toDeclined_toBlocked_toGranted() {
        val vm = viewModel()
        assertEquals(MicrophoneStatus.NOT_ASKED, vm.status.value.microphone)

        // First decline: Android still offers the question again.
        vm.onMicrophoneRequested()
        vm.refresh(showMicrophoneRationale = true)
        assertEquals(MicrophoneStatus.DECLINED, vm.status.value.microphone)

        // Second decline: Android stops showing it.
        vm.refresh(showMicrophoneRationale = false)
        assertEquals(MicrophoneStatus.BLOCKED, vm.status.value.microphone)

        // The user allows it in Android settings.
        checker.microphone = true
        vm.refresh(showMicrophoneRationale = false)
        assertEquals(MicrophoneStatus.GRANTED, vm.status.value.microphone)
    }

    @Test
    fun requestingTheMicrophone_isRemembered() {
        val vm = viewModel()
        assertFalse(repo.current.microphonePermissionAsked)
        vm.onMicrophoneRequested()
        assertTrue(repo.current.microphonePermissionAsked)
    }

    @Test
    fun theTranslationNotificationPermissionIsReadFromAndroid() {
        val vm = viewModel()
        assertFalse(vm.status.value.postNotifications)
        checker.postNotifications = true
        vm.refresh(showMicrophoneRationale = false)
        assertTrue(vm.status.value.postNotifications)
        checker.postNotifications = false // switched off in Android settings
        vm.refresh(showMicrophoneRationale = false)
        assertFalse(vm.status.value.postNotifications)
    }

    @Test
    fun theOverlayPermissionIsReadFromAndroid() {
        val vm = viewModel()
        assertFalse(vm.status.value.overlayPermission)
        checker.overlayPermission = true
        vm.refresh(showMicrophoneRationale = false)
        assertTrue(vm.status.value.overlayPermission)
        checker.overlayPermission = false // switched off in Android settings
        vm.refresh(showMicrophoneRationale = false)
        assertFalse(vm.status.value.overlayPermission)
    }

    @Test
    fun theAccessibilityServiceStatusIsReadFromAndroid() {
        val vm = viewModel()
        assertFalse(vm.status.value.accessibilityServiceEnabled)
        checker.accessibilityService = true
        vm.refresh(showMicrophoneRationale = false)
        assertTrue(vm.status.value.accessibilityServiceEnabled)
        checker.accessibilityService = false // switched off in Android settings
        vm.refresh(showMicrophoneRationale = false)
        assertFalse(vm.status.value.accessibilityServiceEnabled)
    }

    @Test
    fun neverAskedIsNotMistakenForBlocked() {
        // No rationale and no request yet must not look like a permanent block.
        val vm = viewModel()
        vm.refresh(showMicrophoneRationale = false)
        assertEquals(MicrophoneStatus.NOT_ASKED, vm.status.value.microphone)
    }
}
