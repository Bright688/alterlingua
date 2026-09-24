package com.alterlingua.app.ui.setup

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import com.alterlingua.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alterlingua.app.AppViewModelProvider
import com.alterlingua.app.setup.MicrophoneStatus
import com.alterlingua.app.setup.SetupStatus
import com.alterlingua.app.setup.SetupViewModel
import com.alterlingua.app.setup.SystemSettings
import com.alterlingua.app.setup.openSettings
import com.alterlingua.app.ui.theme.extendedColors

/** What the setup screens can ask Android to do. Each one is triggered only by the user tapping its own button. */
class SetupActions(
    val onOpenKeyboardSettings: () -> Unit,
    val onChooseKeyboard: () -> Unit,
    val onOpenNotificationAccess: () -> Unit,
    val onOpenAppInfo: () -> Unit,
    val onRequestMicrophone: () -> Unit,
    val onRequestPostNotifications: () -> Unit,
    val onOpenNotificationSettings: () -> Unit,
    val onOpenOverlayPermission: () -> Unit,
    val onOpenAccessibilitySettings: () -> Unit,
    val onEnableFloatingTranslation: () -> Unit,
    val onAgreeToLiveChatTranslation: () -> Unit,
) {
    companion object {
        val None = SetupActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/** The current setup status together with the actions, passed to screens as one thing. */
class SetupUi(val status: SetupStatus = SetupStatus(), val actions: SetupActions = SetupActions.None) {
    companion object {
        val None = SetupUi()
    }
}

/**
 * Connects the setup screens to Android: keeps [SetupStatus] fresh, and provides the actions.
 *
 * Android has no way to tell an app "the user just changed a system setting", so the status is re-read whenever the
 * app returns to the front and whenever its window regains focus (which also covers the keyboard chooser dialog).
 */
@Composable
fun rememberSetupUi(viewModel: SetupViewModel = viewModel(factory = AppViewModelProvider.Factory)): SetupUi {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()

    fun refresh() {
        val activity = context.findActivity()
        val rationale = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
        viewModel.refresh(showMicrophoneRationale = rationale)
    }
    val currentRefresh by rememberUpdatedState(::refresh)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { currentRefresh() }
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }.collect { focused -> if (focused) currentRefresh() }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { currentRefresh() }
    val notificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { currentRefresh() }

    return remember(status, context) {
        SetupUi(
            status = status,
            actions = SetupActions(
                onOpenKeyboardSettings = { context.openSettings(SystemSettings.keyboardList()) },
                onChooseKeyboard = { context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker() },
                onOpenNotificationAccess = {
                    // Newer phones open AlterLingua's own page; if that is missing try the list, then the app info.
                    context.openSettings(SystemSettings.notificationAccess(context), SystemSettings.notificationAccessList()) ||
                        context.openSettings(SystemSettings.appDetails(context))
                },
                onOpenAppInfo = { context.openSettings(SystemSettings.appDetails(context)) },
                onRequestMicrophone = {
                    viewModel.onMicrophoneRequested()
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onRequestPostNotifications = {
                    // Android 13+ asks with its own question; older versions have nothing to ask, so open the settings.
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.openSettings(SystemSettings.appNotificationSettings(context))
                    }
                },
                onOpenNotificationSettings = { context.openSettings(SystemSettings.appNotificationSettings(context), SystemSettings.appDetails(context)) },
                onOpenOverlayPermission = { context.openSettings(SystemSettings.overlayPermission(context), SystemSettings.appDetails(context)) },
                onOpenAccessibilitySettings = { context.openSettings(SystemSettings.accessibilitySettings()) },
                onEnableFloatingTranslation = {
                    viewModel.onFloatingTranslationEnabled()
                    context.openSettings(SystemSettings.overlayPermission(context), SystemSettings.appDetails(context))
                },
                onAgreeToLiveChatTranslation = {
                    viewModel.onLiveChatTranslationAgreed()
                    context.openSettings(SystemSettings.accessibilitySettings())
                },
            ),
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ---- Words for each status, shared by onboarding, the summary and Settings ----

@Composable
internal fun keyboardStatusLabel(status: SetupStatus): String = stringResource(
    when {
        status.keyboardReady -> R.string.setup_status_ready
        status.keyboardEnabled -> R.string.setup_status_on_not_chosen
        else -> R.string.setup_status_not_on
    },
)

@Composable
internal fun notificationStatusLabel(status: SetupStatus): String =
    stringResource(if (status.notificationAccess) R.string.setup_access_allowed else R.string.setup_not_allowed)

@Composable
internal fun postNotificationsStatusLabel(status: SetupStatus): String =
    stringResource(if (status.postNotifications) R.string.setup_allowed else R.string.setup_not_allowed)

@Composable
internal fun overlayPermissionStatusLabel(status: SetupStatus): String =
    stringResource(if (status.overlayPermission) R.string.setup_allowed else R.string.setup_not_allowed)

@Composable
internal fun accessibilityServiceStatusLabel(status: SetupStatus): String =
    stringResource(if (status.accessibilityServiceEnabled) R.string.setup_allowed else R.string.setup_not_allowed)

@Composable
internal fun microphoneStatusLabel(status: SetupStatus): String = stringResource(
    when (status.microphone) {
        MicrophoneStatus.GRANTED -> R.string.setup_allowed
        MicrophoneStatus.NOT_ASKED -> R.string.setup_not_allowed_yet
        MicrophoneStatus.DECLINED -> R.string.setup_not_allowed
        MicrophoneStatus.BLOCKED -> R.string.setup_blocked
    },
)

// ---- Shared pieces ----

@Composable
internal fun SetupStatusChip(text: String, done: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (done) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.extendedColors.chipSurface,
        border = if (done) null else BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (done) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One thing to set up: title, live status, a plain explanation and its buttons. */
@Composable
internal fun SetupItemCard(
    title: String,
    statusText: String,
    done: Boolean,
    description: String,
    statusTag: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.extendedColors.card,
        border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                SetupStatusChip(statusText, done, Modifier.testTag(statusTag))
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
internal fun SetupPrimaryButton(text: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(text) }
}

@Composable
internal fun SetupSecondaryButton(text: String, tag: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(text) }
}

/**
 * The "Setup" block in Settings. Every row says in plain words what its button does before it is tapped,
 * so the microphone question is never shown without an explanation next to it.
 */
@Composable
internal fun SetupSettingsRows(setup: SetupUi) {
    val status = setup.status
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsSetupRow(
            title = stringResource(R.string.setup_keyboard_title),
            statusText = keyboardStatusLabel(status),
            done = status.keyboardReady,
            statusTag = "settings_status_keyboard",
            description = stringResource(R.string.setup_keyboard_desc),
            buttonText = stringResource(
                when {
                    !status.keyboardEnabled -> R.string.setup_turn_on_keyboard
                    !status.keyboardSelected -> R.string.onb_choose_keyboard
                    else -> R.string.setup_keyboard_settings
                },
            ),
            buttonTag = "settings_keyboard_action",
            onClick = if (status.keyboardEnabled && !status.keyboardSelected) {
                setup.actions.onChooseKeyboard
            } else {
                setup.actions.onOpenKeyboardSettings
            },
        )
        SettingsSetupRow(
            title = stringResource(R.string.onb_incoming_messages),
            statusText = notificationStatusLabel(status),
            done = status.notificationAccess,
            statusTag = "settings_status_notifications",
            description = stringResource(R.string.setup_notif_desc),
            buttonText = stringResource(R.string.onb_notification_access),
            buttonTag = "settings_notifications_action",
            onClick = setup.actions.onOpenNotificationAccess,
        )
        SettingsSetupRow(
            title = stringResource(R.string.setup_translation_notifications),
            statusText = postNotificationsStatusLabel(status),
            done = status.postNotifications,
            statusTag = "settings_status_post_notifications",
            description = stringResource(R.string.setup_post_desc),
            buttonText = stringResource(if (status.postNotifications) R.string.onb_notification_settings else R.string.onb_allow_notifications),
            buttonTag = "settings_post_notifications_action",
            onClick = if (status.postNotifications) setup.actions.onOpenNotificationSettings else setup.actions.onRequestPostNotifications,
        )
        SettingsSetupRow(
            title = stringResource(R.string.onb_step_microphone),
            statusText = microphoneStatusLabel(status),
            done = status.microphoneGranted,
            statusTag = "settings_status_microphone",
            description = stringResource(
                when (status.microphone) {
                    MicrophoneStatus.GRANTED -> R.string.setup_mic_granted
                    MicrophoneStatus.BLOCKED -> R.string.setup_mic_blocked
                    else -> R.string.setup_mic_ask
                },
            ),
            buttonText = stringResource(
                when (status.microphone) {
                    MicrophoneStatus.GRANTED, MicrophoneStatus.BLOCKED -> R.string.setup_app_settings
                    else -> R.string.voice_allow
                },
            ),
            buttonTag = "settings_microphone_action",
            onClick = when (status.microphone) {
                MicrophoneStatus.GRANTED, MicrophoneStatus.BLOCKED -> setup.actions.onOpenAppInfo
                else -> setup.actions.onRequestMicrophone
            },
        )
    }
}

@Composable
private fun SettingsSetupRow(
    title: String,
    statusText: String,
    done: Boolean,
    statusTag: String,
    description: String,
    buttonText: String,
    buttonTag: String,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            SetupStatusChip(statusText, done, Modifier.testTag(statusTag))
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onClick, modifier = Modifier.testTag(buttonTag)) { Text(buttonText) }
    }
}
