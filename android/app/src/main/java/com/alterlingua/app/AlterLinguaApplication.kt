package com.alterlingua.app

import android.app.Application
import com.alterlingua.app.setup.AndroidSetupChecker
import com.alterlingua.app.setup.SetupChecker
import com.alterlingua.app.storage.DataStoreUserSettingsRepository
import com.alterlingua.app.storage.UserSettingsRepository
import com.alterlingua.app.learning.engine.AndroidIcuWordBreaker
import com.alterlingua.app.learning.engine.AnalyzerRegistry
import com.alterlingua.app.learning.engine.ExposureStore
import com.alterlingua.app.learning.assistance.OnDemandHelp
import com.alterlingua.app.learning.lessons.DailyLessonStore
import com.alterlingua.app.learning.lessons.DataStoreDailyLessonStore
import com.alterlingua.app.privacy.LearningDataEraser
import com.alterlingua.app.privacy.TemporaryAudioFolders
import com.alterlingua.app.learning.progress.MeteredLearningRecorder
import com.alterlingua.app.learning.progress.ProgressDatabase
import com.alterlingua.app.learning.progress.ProgressLog
import com.alterlingua.app.learning.progress.ProgressService
import com.alterlingua.app.learning.progress.RoomProgressStore
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.lessons.LessonService
import com.alterlingua.app.learning.lessons.TranslationMeaningProvider
import com.alterlingua.app.learning.map.LanguageMapDatabase
import com.alterlingua.app.learning.map.LanguageMapExposureStore
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.learning.map.RoomLanguageMapStore
import com.alterlingua.app.learning.engine.LearningContext
import com.alterlingua.app.learning.engine.LearningPipeline
import com.alterlingua.app.learning.engine.LearningRecorder
import com.alterlingua.app.learning.engine.PipelineRecorder
import com.alterlingua.app.learning.engine.RuleBasedAnalyzer
import com.alterlingua.app.accessibility.LiveChatStatus
import com.alterlingua.app.accessibility.LiveChatTranslator
import com.alterlingua.app.notifications.IncomingStatus
import com.alterlingua.app.notifications.IncomingTranslator
import com.alterlingua.app.notifications.SeenMessages
import com.alterlingua.app.notifications.TranslatedNotificationPresenter
import com.alterlingua.app.translation.AndroidConnectivity
import com.alterlingua.app.translation.HttpTranslationApi
import com.alterlingua.app.translation.HttpVoiceApi
import com.alterlingua.app.translation.TranslationApi
import com.alterlingua.app.translation.VoiceApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Holds the app-wide objects that screens' ViewModels need. Created once when the app starts. */
class AlterLinguaApplication : Application() {
    val userSettings: UserSettingsRepository by lazy { DataStoreUserSettingsRepository.create(this) }
    val setupChecker: SetupChecker by lazy { AndroidSetupChecker(this) }

    /** The translation service used by the keyboard. Its address is set in the build (see app/build.gradle.kts). */
    val translationApi: TranslationApi by lazy {
        HttpTranslationApi(BuildConfig.TRANSLATION_BASE_URL, isOnline = AndroidConnectivity(this)::isOnline, apiToken = BuildConfig.API_TOKEN)
    }

    /**
     * The Personal Language Map: the units met and how well each is known, per language, saved in a private database on
     * the phone. It holds units and counts only, never messages.
     */
    val languageMap: LanguageMapService by lazy {
        LanguageMapService(RoomLanguageMapStore(LanguageMapDatabase.create(this)), progress = progressLog)
    }

    /** Daily counts (numbers only) that the Progress tab is drawn from, in their own private database. */
    val progressLog: ProgressLog by lazy {
        ProgressLog(RoomProgressStore(ProgressDatabase.create(this)), mode = { userSettings.settings.first().assistanceMode })
    }

    /** Builds the Progress report from the real map and daily counts. */
    val progressService: ProgressService by lazy { ProgressService(languageMap, progressLog) }

    /** Today's saved lesson (terms, meanings and counts only). */
    val lessonStore: DailyLessonStore by lazy { DataStoreDailyLessonStore.create(this) }

    /** The temporary audio folders, for the start-up clean-up and for erasing data. */
    val temporaryAudio: TemporaryAudioFolders by lazy { TemporaryAudioFolders(cacheDir) }

    /** "Delete all learning data" in Settings. */
    val learningDataEraser: LearningDataEraser by lazy {
        LearningDataEraser(languageMap, progressLog, lessonStore, temporaryAudio, forgetMessages = { incomingTranslator.clear(); liveChatTranslator.clear(); liveChatStatus.clear(); capturedNotes.clearAll() })
    }

    /** The chosen app language, kept up to date for code that cannot wait for a settings read (null: the phone's language). */
    @Volatile
    private var appLanguageNow: com.alterlingua.app.learning.Language? = null

    /** Which notification messages have already been offered for translation (cleared by "Delete all learning data"). */
    private val seenMessages: SeenMessages by lazy { SeenMessages() }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            userSettings.settings.collect {
                appLanguageNow = it.appLanguage.takeIf { _ -> it.appLanguageChosen }
            }
        }
        // Audio left behind by a crash or a killed process is removed; recordings younger than an hour are left alone.
        Thread({ temporaryAudio.sweep() }, "alterlingua-audio-cleanup").apply { isDaemon = true }.start()
    }

    /** The daily micro-lesson: chosen from the Personal Language Map, kept for the day, and recorded as a mastery signal. */
    val lessonService: LessonService by lazy {
        LessonService(
            map = languageMap,
            meanings = TranslationMeaningProvider(translationApi),
            store = lessonStore,
            languages = { userSettings.settings.first().let { LessonLanguages(it.targetLanguage, it.nativeLanguage) } },
            progress = progressLog,
        )
    }

    /** The language-aware text analysers (word breaking for every supported language). */
    val analyzers: AnalyzerRegistry by lazy { AnalyzerRegistry(listOf(RuleBasedAnalyzer(cjkBreaker = AndroidIcuWordBreaker))) }

    /** On-demand help on AlterLingua's own screens: a word's meaning only when asked for, recorded as a mastery signal. */
    val onDemandHelp: OnDemandHelp by lazy {
        OnDemandHelp(
            analyzers = analyzers,
            map = languageMap,
            meanings = TranslationMeaningProvider(translationApi),
            languages = { userSettings.settings.first().let { LessonLanguages(it.targetLanguage, it.nativeLanguage) } },
        )
    }

    /** Where the learning pipeline puts what it finds: straight into the Personal Language Map. */
    val learningStore: ExposureStore by lazy { LanguageMapExposureStore(languageMap) }

    /** Picks useful words and phrases out of translated text in the language being learned, then forgets the text. */
    val learningRecorder: LearningRecorder by lazy {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        MeteredLearningRecorder(
            delegate = PipelineRecorder(learningPipeline, scope),
            log = progressLog,
            learningLanguage = { userSettings.settings.first().targetLanguage.code },
            scope = scope,
        )
    }

    /** The learning pipeline itself, for callers that need to know whether anything was saved. */
    val learningPipeline: LearningPipeline by lazy {
        LearningPipeline(
            analyzers = analyzers,
            context = { userSettings.settings.first().let { LearningContext(it.targetLanguage, it.nativeLanguage, it.learningFromMessagesEnabled) } },
            store = learningStore,
        )
    }

    /** The latest incoming-translation outcome (never message text), for Settings to show. */
    val incomingStatus: IncomingStatus by lazy { IncomingStatus() }

    /**
     * Translates incoming messages from a supported chat app (see IncomingSources) into the user's own language. Its
     * memory (which messages were seen and the text of the translated notifications) lives only in this process and
     * is never saved.
     */
    val incomingTranslator: IncomingTranslator by lazy {
        IncomingTranslator(
            api = translationApi,
            nativeLanguage = { userSettings.settings.first().nativeLanguage },
            enabled = { userSettings.settings.first().incomingTranslationEnabled },
            presenter = TranslatedNotificationPresenter(this, appLanguage = { appLanguageNow }),
            seen = seenMessages,
            outcomes = incomingStatus,
            learning = learningRecorder,
            assistanceMode = { userSettings.settings.first().assistanceMode },
        )
    }

    /**
     * Translates the messages read live off a supported chat app's own screen while it is open (see
     * AlterLinguaAccessibilityService), which draws each translation directly under its message. Off by default, and
     * inert without Android's Accessibility permission. Its answers are kept in memory only.
     */
    val liveChatStatus: LiveChatStatus by lazy { LiveChatStatus() }

    val liveChatTranslator: LiveChatTranslator by lazy {
        LiveChatTranslator(
            api = translationApi,
            nativeLanguage = { userSettings.settings.first().nativeLanguage },
            enabled = { userSettings.settings.first().liveChatTranslationEnabled },
            networkContext = Dispatchers.IO,
            learning = learningRecorder,
        )
    }

    /** Voice notes captured from chat apps and their translations, in memory only (see CapturedNotes). */
    val capturedNotes: com.alterlingua.app.capture.CapturedNotes by lazy {
        com.alterlingua.app.capture.CapturedNotes(
            create = { store -> com.alterlingua.app.capture.newCapturedNoteViewModel(this, store) },
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default),
            // When a voice note has been transcribed and translated, a quiet notification says so, unless the user muted it in Settings.
            onTranslated = { note ->
                if (userSettings.settings.first().notifyOnCapturedNotes) com.alterlingua.app.capture.CapturedNoteNotifier(this).notifyReady(note.address)
            },
        )
    }

    /** The voice translation service used by the keyboard's microphone. */
    val voiceApi: HttpVoiceApi by lazy {
        HttpVoiceApi(BuildConfig.TRANSLATION_BASE_URL, isOnline = AndroidConnectivity(this)::isOnline, apiToken = BuildConfig.API_TOKEN)
    }
}
