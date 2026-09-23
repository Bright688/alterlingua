package com.alterlingua.app.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alterlingua.app.learning.AssistanceMode
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.KeyboardStyle
import com.alterlingua.app.learning.LanguageLevel
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.LearningPurpose
import com.alterlingua.app.learning.UserSettings
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Reads and saves the user's settings. The app talks to this interface, not to DataStore directly. */
interface UserSettingsRepository {
    /** Emits the saved settings, and again whenever they change. Missing values fall back to defaults. */
    val settings: Flow<UserSettings>

    /** Applies [transform] to the latest saved settings and saves the result. */
    suspend fun update(transform: (UserSettings) -> UserSettings)
}

/** Names of the saved values. Changing one of these would lose existing users' saved data. */
internal object SettingsKeys {
    val OnboardingCompleted = booleanPreferencesKey("onboarding_completed")
    val WelcomeSeen = booleanPreferencesKey("welcome_seen")
    val NativeLanguage = stringPreferencesKey("native_language")
    val TargetLanguage = stringPreferencesKey("target_language")
    val AppLanguage = stringPreferencesKey("app_language")
    val AppLanguageChosen = booleanPreferencesKey("app_language_chosen")
    val DetectSourceAutomatically = booleanPreferencesKey("detect_source_automatically")
    val AutoTranslateEnabled = booleanPreferencesKey("auto_translate_enabled")
    fun keyboardStyleFor(code: String) = stringPreferencesKey("keyboard_style_$code")
    val Purpose = stringPreferencesKey("learning_purpose")
    /** The level for the current target language (also the only level older versions saved). */
    val Level = stringPreferencesKey("language_level")

    /** One level per language, so each learning language keeps its own answer. */
    fun levelFor(languageCode: String) = stringPreferencesKey("language_level_$languageCode")
    val AssistanceMode = stringPreferencesKey("assistance_mode")
    val DailyReminderEnabled = booleanPreferencesKey("daily_reminder_enabled")
    val ReminderMinuteOfDay = intPreferencesKey("reminder_minute_of_day")
    val MicrophonePermissionAsked = booleanPreferencesKey("microphone_permission_asked")
    val IncomingTranslationEnabled = booleanPreferencesKey("incoming_translation_enabled")
    val LearningFromMessagesEnabled = booleanPreferencesKey("learning_from_messages_enabled")
}

private val Context.userSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class DataStoreUserSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : UserSettingsRepository {

    override val settings: Flow<UserSettings> = dataStore.data
        .catch { error ->
            // A damaged file must not crash the app: fall back to defaults.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it.toUserSettings() }

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toUserSettings())) }
    }

    companion object {
        fun create(context: Context): DataStoreUserSettingsRepository =
            DataStoreUserSettingsRepository(context.applicationContext.userSettingsDataStore)
    }
}

/** Reads settings, ignoring any value that is missing or not recognised (so old or damaged data cannot crash the app). */
internal fun Preferences.toUserSettings(): UserSettings {
    val defaults = UserSettings()
    val minute = this[SettingsKeys.ReminderMinuteOfDay]
    val target = Languages.fromCode(this[SettingsKeys.TargetLanguage]) ?: defaults.targetLanguage
    val savedLevels = Languages.supported.mapNotNull { language ->
        this[SettingsKeys.levelFor(language.code)]
            ?.let { name -> runCatching { enumValueOf<LanguageLevel>(name) }.getOrNull() }
            ?.let { language.code to it }
    }.toMap()
    return UserSettings(
        onboardingCompleted = this[SettingsKeys.OnboardingCompleted] ?: defaults.onboardingCompleted,
        welcomeSeen = this[SettingsKeys.WelcomeSeen] ?: defaults.welcomeSeen,
        nativeLanguage = Languages.fromCode(this[SettingsKeys.NativeLanguage]) ?: defaults.nativeLanguage,
        targetLanguage = target,
        appLanguage = Languages.fromCode(this[SettingsKeys.AppLanguage]) ?: defaults.appLanguage,
        appLanguageChosen = this[SettingsKeys.AppLanguageChosen] ?: defaults.appLanguageChosen,
        detectSourceAutomatically = this[SettingsKeys.DetectSourceAutomatically] ?: defaults.detectSourceAutomatically,
        autoTranslateEnabled = this[SettingsKeys.AutoTranslateEnabled] ?: defaults.autoTranslateEnabled,
        purpose = enumOrDefault(this[SettingsKeys.Purpose], defaults.purpose),
        level = savedLevels[target.code] ?: enumOrDefault(this[SettingsKeys.Level], defaults.level),
        otherLevels = savedLevels - target.code,
        assistanceMode = enumOrDefault(this[SettingsKeys.AssistanceMode], defaults.assistanceMode),
        dailyReminderEnabled = this[SettingsKeys.DailyReminderEnabled] ?: defaults.dailyReminderEnabled,
        reminderTime = if (minute != null && minute in 0 until MINUTES_PER_DAY) {
            LocalTime.of(minute / 60, minute % 60)
        } else {
            defaults.reminderTime
        },
        microphonePermissionAsked = this[SettingsKeys.MicrophonePermissionAsked] ?: defaults.microphonePermissionAsked,
        incomingTranslationEnabled = this[SettingsKeys.IncomingTranslationEnabled] ?: defaults.incomingTranslationEnabled,
        learningFromMessagesEnabled = this[SettingsKeys.LearningFromMessagesEnabled] ?: defaults.learningFromMessagesEnabled,
        keyboardStyles = Languages.supported.mapNotNull { language ->
            this[SettingsKeys.keyboardStyleFor(language.code)]
                ?.let { name -> runCatching { enumValueOf<KeyboardStyle>(name) }.getOrNull() }
                ?.let { language.code to it }
        }.toMap(),
    )
}

private fun androidx.datastore.preferences.core.MutablePreferences.write(settings: UserSettings) {
    this[SettingsKeys.OnboardingCompleted] = settings.onboardingCompleted
    this[SettingsKeys.WelcomeSeen] = settings.welcomeSeen
    this[SettingsKeys.NativeLanguage] = settings.nativeLanguage.code
    this[SettingsKeys.TargetLanguage] = settings.targetLanguage.code
    this[SettingsKeys.AppLanguage] = settings.appLanguage.code
    this[SettingsKeys.AppLanguageChosen] = settings.appLanguageChosen
    this[SettingsKeys.DetectSourceAutomatically] = settings.detectSourceAutomatically
    this[SettingsKeys.AutoTranslateEnabled] = settings.autoTranslateEnabled
    settings.keyboardStyles.forEach { (code, style) -> this[SettingsKeys.keyboardStyleFor(code)] = style.name }
    this[SettingsKeys.Purpose] = settings.purpose.name
    this[SettingsKeys.Level] = settings.level.name
    settings.otherLevels.forEach { (code, level) -> this[SettingsKeys.levelFor(code)] = level.name }
    this[SettingsKeys.levelFor(settings.targetLanguage.code)] = settings.level.name
    this[SettingsKeys.AssistanceMode] = settings.assistanceMode.name
    this[SettingsKeys.DailyReminderEnabled] = settings.dailyReminderEnabled
    this[SettingsKeys.ReminderMinuteOfDay] = settings.reminderTime.hour * 60 + settings.reminderTime.minute
    this[SettingsKeys.MicrophonePermissionAsked] = settings.microphonePermissionAsked
    this[SettingsKeys.IncomingTranslationEnabled] = settings.incomingTranslationEnabled
    this[SettingsKeys.LearningFromMessagesEnabled] = settings.learningFromMessagesEnabled
}

private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

private const val MINUTES_PER_DAY = 24 * 60

/** Saves a new translation target language. Used by the keyboard toolbar. */
suspend fun UserSettingsRepository.setTargetLanguage(language: Language) {
    update { it.withTargetLanguage(language) }
}
