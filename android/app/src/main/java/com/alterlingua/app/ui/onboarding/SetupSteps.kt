package com.alterlingua.app.ui.onboarding

import android.os.Build
import com.alterlingua.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alterlingua.app.setup.MicrophoneStatus
import com.alterlingua.app.ui.setup.SetupItemCard
import com.alterlingua.app.ui.setup.SetupPrimaryButton
import com.alterlingua.app.ui.setup.SetupSecondaryButton
import com.alterlingua.app.ui.setup.SetupUi
import com.alterlingua.app.ui.setup.accessibilityServiceStatusLabel
import com.alterlingua.app.ui.setup.keyboardStatusLabel
import com.alterlingua.app.ui.setup.microphoneStatusLabel
import com.alterlingua.app.ui.setup.notificationStatusLabel
import com.alterlingua.app.ui.setup.overlayPermissionStatusLabel
import com.alterlingua.app.ui.setup.postNotificationsStatusLabel

// ---------------------------------------------------------------------------------------------
// Steps 5 to 7: system setup. Each step explains first; only its own button touches Android.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun KeyboardStep(setup: SetupUi) {
    val status = setup.status
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepTitle(
            title = stringResource(R.string.onb_your_keyboard),
            subtitle = stringResource(R.string.onb_alterlingua_works_through_its_own),
        )
        SetupItemCard(
            title = stringResource(R.string.onb_turn_the_keyboard_on),
            statusText = if (status.keyboardEnabled) "On" else "Off",
            done = status.keyboardEnabled,
            statusTag = "setup_status_keyboard_enabled",
            description = stringResource(R.string.onb_android_keeps_every_keyboard_switched),
        ) {
            SetupPrimaryButton(
                text = stringResource(if (status.keyboardEnabled) R.string.onb_open_keyboard_settings_again else R.string.onb_open_keyboard_settings),
                tag = "setup_keyboard_enable",
                onClick = setup.actions.onOpenKeyboardSettings,
            )
        }
        SetupItemCard(
            title = stringResource(R.string.onb_choose_it_as_your_keyboard),
            statusText = stringResource(if (status.keyboardSelected) R.string.onb_in_use else R.string.onb_not_in_use),
            done = status.keyboardSelected,
            statusTag = "setup_status_keyboard_selected",
            description = if (status.keyboardEnabled) {
                stringResource(R.string.onb_keyboard_choose_help)
            } else {
                stringResource(R.string.onb_turn_keyboard_first)
            },
        ) {
            SetupPrimaryButton(
                text = stringResource(R.string.onb_choose_keyboard),
                tag = "setup_keyboard_choose",
                enabled = status.keyboardEnabled,
                onClick = setup.actions.onChooseKeyboard,
            )
        }
        NoteRow(Icons.Filled.Lock, stringResource(R.string.onb_alterlingua_never_sends_a_message))
    }
}

@Composable
internal fun NotificationsStep(setup: SetupUi) {
    val status = setup.status
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepTitle(
            title = stringResource(R.string.onb_incoming_messages),
            subtitle = stringResource(R.string.onb_later_alterlingua_can_help_you),
        )
        SetupItemCard(
            title = stringResource(R.string.onb_notification_access),
            statusText = notificationStatusLabel(status),
            done = status.notificationAccess,
            statusTag = "setup_status_notifications",
            description = stringResource(R.string.onb_android_calls_this_notification_access),
        ) {
            SetupPrimaryButton(
                text = stringResource(if (status.notificationAccess) R.string.onb_open_notification_access_again else R.string.onb_open_notification_access),
                tag = "setup_notifications_open",
                onClick = setup.actions.onOpenNotificationAccess,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !status.notificationAccess) {
                Text(
                    text = stringResource(R.string.onb_if_the_switch_is_greyed),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SetupSecondaryButton(stringResource(R.string.onb_open_app_info), "setup_notifications_app_info", setup.actions.onOpenAppInfo)
            }
        }
        SetupItemCard(
            title = stringResource(R.string.onb_show_translations),
            statusText = postNotificationsStatusLabel(status),
            done = status.postNotifications,
            statusTag = "setup_status_post_notifications",
            description = stringResource(R.string.onb_alterlingua_shows_each_translated_message),
        ) {
            if (status.postNotifications) {
                SetupSecondaryButton(stringResource(R.string.onb_notification_settings), "setup_post_notifications_settings", setup.actions.onOpenNotificationSettings)
            } else {
                SetupPrimaryButton(stringResource(R.string.onb_allow_notifications), "setup_post_notifications_allow", onClick = setup.actions.onRequestPostNotifications)
                SetupSecondaryButton(stringResource(R.string.onb_notification_settings), "setup_post_notifications_settings", setup.actions.onOpenNotificationSettings)
            }
        }
        NoteRow(Icons.Filled.Info, stringResource(R.string.onb_you_can_turn_this_off))
    }
}

@Composable
internal fun FloatingTranslationStep(setup: SetupUi) {
    val status = setup.status
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepTitle(
            title = stringResource(R.string.set_floating_translation),
            subtitle = stringResource(R.string.onb_floating_translation_subtitle),
        )
        SetupItemCard(
            title = stringResource(R.string.set_floating_translation),
            statusText = overlayPermissionStatusLabel(status),
            done = status.overlayPermission,
            statusTag = "setup_status_overlay",
            description = stringResource(R.string.set_floating_translation_desc),
        ) {
            if (status.overlayPermission) {
                SetupSecondaryButton(stringResource(R.string.setup_app_settings), "setup_overlay_settings", setup.actions.onOpenOverlayPermission)
            } else {
                SetupPrimaryButton(stringResource(R.string.set_turn_on), "setup_overlay_turn_on", onClick = setup.actions.onEnableFloatingTranslation)
            }
        }
        NoteRow(Icons.Filled.Info, stringResource(R.string.onb_you_can_turn_this_off))
    }
}

@Composable
internal fun LiveChatTranslationStep(setup: SetupUi) {
    val status = setup.status
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepTitle(
            title = stringResource(R.string.set_live_chat_translation),
            subtitle = stringResource(R.string.onb_live_chat_translation_subtitle),
        )
        SetupItemCard(
            title = stringResource(R.string.set_live_chat_translation),
            statusText = accessibilityServiceStatusLabel(status),
            done = status.accessibilityServiceEnabled,
            statusTag = "setup_status_accessibility",
            description = stringResource(R.string.set_live_chat_consent_body),
        ) {
            if (status.accessibilityServiceEnabled) {
                SetupSecondaryButton(stringResource(R.string.setup_app_settings), "setup_accessibility_settings", setup.actions.onOpenAccessibilitySettings)
            } else {
                SetupPrimaryButton(stringResource(R.string.set_turn_on), "setup_accessibility_turn_on", onClick = setup.actions.onAgreeToLiveChatTranslation)
            }
        }
        NoteRow(Icons.Filled.Info, stringResource(R.string.onb_live_chat_translation_note))
    }
}

@Composable
internal fun MicrophoneStep(setup: SetupUi) {
    val status = setup.status
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepTitle(
            title = stringResource(R.string.toolbar_microphone),
            subtitle = stringResource(R.string.onb_later_you_ll_be_able),
        )
        SetupItemCard(
            title = stringResource(R.string.onb_microphone_permission),
            statusText = microphoneStatusLabel(status),
            done = status.microphone == MicrophoneStatus.GRANTED,
            statusTag = "setup_status_microphone",
            description = stringResource(R.string.onb_tapping_the_button_below_makes),
        ) {
            when (status.microphone) {
                MicrophoneStatus.GRANTED -> Unit
                MicrophoneStatus.NOT_ASKED -> SetupPrimaryButton(stringResource(R.string.voice_allow), "setup_microphone_allow", onClick = setup.actions.onRequestMicrophone)
                MicrophoneStatus.DECLINED -> {
                    Text(
                        text = stringResource(R.string.onb_you_chose_not_to_allow),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SetupPrimaryButton(stringResource(R.string.onb_ask_again), "setup_microphone_allow", onClick = setup.actions.onRequestMicrophone)
                }
                MicrophoneStatus.BLOCKED -> {
                    Text(
                        text = stringResource(R.string.onb_android_won_t_show_the),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SetupSecondaryButton(stringResource(R.string.onb_open_app_settings), "setup_microphone_app_settings", setup.actions.onOpenAppInfo)
                }
            }
        }
        NoteRow(Icons.Filled.Lock, stringResource(R.string.onb_typing_always_works_without_the))
    }
}
