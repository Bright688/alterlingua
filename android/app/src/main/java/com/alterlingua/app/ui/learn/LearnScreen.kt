package com.alterlingua.app.ui.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alterlingua.app.AppViewModelProvider
import com.alterlingua.app.R
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.lessons.CardKind
import com.alterlingua.app.learning.lessons.DailyLesson
import com.alterlingua.app.learning.lessons.LessonCard
import com.alterlingua.app.learning.lessons.LessonContext
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.string
import com.alterlingua.app.ui.components.MasteryChip
import com.alterlingua.app.ui.components.ScreenFrame
import com.alterlingua.app.ui.components.TagPill
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import com.alterlingua.app.ui.theme.extendedColors

/** Which of the Learn tab's curriculum-related screens is showing right now (The Forge's own navigation). */
private enum class CurriculumScreen { CARDS, FORGE_LEVELS, FORGE_LEVEL, LESSON_MENU, SESSION }

/** Google Play's purchase flow needs a real Activity, not just a Context; Compose only hands us the latter directly. */
private tailrec fun findActivity(context: android.content.Context): android.app.Activity? = when (context) {
    is android.app.Activity -> context
    is android.content.ContextWrapper -> findActivity(context.baseContext)
    else -> null
}

@Composable
fun LearnRoute(
    modifier: Modifier = Modifier,
    viewModel: LearnViewModel = viewModel(factory = AppViewModelProvider.Factory),
    curriculumViewModel: CurriculumLessonViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val curriculumState by curriculumViewModel.uiState.collectAsStateWithLifecycle()
    val curriculumListening by curriculumViewModel.listening.collectAsStateWithLifecycle()
    val curriculumCanListen by curriculumViewModel.canListen.collectAsStateWithLifecycle()
    val curriculumTier by curriculumViewModel.tier.collectAsStateWithLifecycle()
    val curriculumOffers by curriculumViewModel.offers.collectAsStateWithLifecycle()
    val forgeLevels by curriculumViewModel.forgeLevels.collectAsStateWithLifecycle()
    val forgeLevel by curriculumViewModel.forgeLevel.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    // New messages may have been translated while the tab was away: look again for today's lesson.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    // Standard and Premium lessons (see CurriculumLessonService) are a second, separate source of lessons: The
    // Forge, one shared curriculum (Language -> Level -> Unit -> Lesson) for every plan, apart from the free micro
    // lesson drawn from the learner's own conversations. Standard and Premium are two separate Google Play products
    // (CLAUDE.md section 32): each gets its own card below, with its own price and its own Subscribe button, never
    // merged into one -- but tapping either opens the SAME curriculum (CLAUDE.md's own "Final decision": one
    // curriculum for all plans, not separate lesson libraries).
    var screen by rememberSaveable { mutableStateOf(CurriculumScreen.CARDS) }
    var openLevelRange by remember { mutableStateOf<com.alterlingua.app.learning.curriculum.CurriculumLevelRange?>(null) }
    var openUnit by remember { mutableStateOf<com.alterlingua.app.learning.curriculum.ForgeUnitSummary?>(null) }
    when (screen) {
        CurriculumScreen.SESSION -> CurriculumLessonScreen(
            state = curriculumState,
            onNext = curriculumViewModel::next,
            onRetry = { curriculumViewModel.start() },
            onUpgrade = { screen = CurriculumScreen.CARDS }, // back to this tab's own Standard/Premium cards to pick a plan
            onLanguageSelected = { language -> curriculumViewModel.start(language) },
            onListen = curriculumViewModel::listen,
            listening = curriculumListening,
            canListen = curriculumCanListen,
            onUnitPracticeDone = {
                openLevelRange?.let { curriculumViewModel.openLevel(it) } // refreshes mastered counts after practicing
                screen = if (openUnit != null) CurriculumScreen.LESSON_MENU else CurriculumScreen.FORGE_LEVEL
            },
            modifier = modifier,
            footer = {
                OutlinedButton(
                    onClick = {
                        screen = when {
                            openUnit != null -> CurriculumScreen.LESSON_MENU
                            openLevelRange != null -> CurriculumScreen.FORGE_LEVEL
                            else -> CurriculumScreen.FORGE_LEVELS
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("lrn_back"),
                ) { Text(stringResource(R.string.lrn_back)) }
            },
        )
        CurriculumScreen.LESSON_MENU -> {
            val unit = openUnit
            if (unit != null) {
                LessonMenuScreen(
                    unit = unit,
                    onOpenVocabulary = { curriculumViewModel.practiceUnit(unit); screen = CurriculumScreen.SESSION },
                    onBack = { screen = CurriculumScreen.FORGE_LEVEL },
                    modifier = modifier,
                )
            } else {
                // openUnit is plain `remember`, not saveable: lost across process death. Falls back up the stack
                // rather than rendering nothing.
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    screen = if (openLevelRange != null) CurriculumScreen.FORGE_LEVEL else CurriculumScreen.FORGE_LEVELS
                }
            }
        }
        CurriculumScreen.FORGE_LEVEL -> {
            val range = openLevelRange
            if (range != null) {
                ForgeLevelScreen(
                    state = forgeLevel,
                    onOpenUnit = { unit -> openUnit = unit; screen = CurriculumScreen.LESSON_MENU },
                    onBack = { screen = CurriculumScreen.FORGE_LEVELS },
                    onRetry = { curriculumViewModel.openLevel(range) },
                    modifier = modifier,
                )
            } else {
                // openLevelRange is plain `remember`, not saveable: lost across process death. Falls back to the
                // level list rather than rendering nothing.
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    screen = CurriculumScreen.FORGE_LEVELS
                    curriculumViewModel.loadForge()
                }
            }
        }
        CurriculumScreen.FORGE_LEVELS -> ForgeLevelsScreen(
            state = forgeLevels,
            onOpenLevel = { range -> openLevelRange = range; curriculumViewModel.openLevel(range); screen = CurriculumScreen.FORGE_LEVEL },
            onContinue = { curriculumViewModel.start(); screen = CurriculumScreen.SESSION },
            onRetry = { curriculumViewModel.loadForge() },
            onBack = { screen = CurriculumScreen.CARDS },
            modifier = modifier,
        )
        CurriculumScreen.CARDS -> LearnScreen(
            state = state,
            onPrevious = viewModel::previous,
            onNext = viewModel::next,
            modifier = modifier,
            onRecognition = viewModel::recordRecognition,
            footer = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StandardAndPremiumCards(
                        tier = curriculumTier,
                        offers = curriculumOffers,
                        onOpen = {
                            openLevelRange = null
                            curriculumViewModel.loadForge()
                            screen = CurriculumScreen.FORGE_LEVELS
                        },
                        onSubscribe = { offer -> findActivity(context)?.let { curriculumViewModel.subscribe(it, offer) } },
                    )
                    ReaderRoute()
                }
            },
            practice = { card -> PracticeRoute(card) },
        )
    }
}

@Composable
fun LearnScreen(
    state: LearnUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: Long = System.currentTimeMillis(),
    /** The learner said whether they already knew the card, before being shown whether they were right (see
     * [com.alterlingua.app.learning.map.LanguageMapService.recordRecognition]): this is the recall signal Adaptive
     * mode needs, separate from and stronger than pronunciation practice's imitation. */
    onRecognition: (LessonCard, Boolean) -> Unit = { _, _ -> },
    /** Extra content under the lesson, e.g. the On-demand reader. */
    footer: @Composable () -> Unit = {},
    /** Listen, Repeat and feedback for a card. The default is the disabled placeholder. */
    practice: @Composable (LessonCard) -> Unit = { PracticePlaceholder() },
) {
    ScreenFrame(
        title = stringResource(R.string.nav_learn),
        testTag = "screen_learn",
        modifier = modifier,
    ) {
        when (state) {
            LearnUiState.Loading -> Unit
            is LearnUiState.Empty -> EmptyLesson(state)
            is LearnUiState.Card -> LessonCardContent(state, onPrevious, onNext, nowMillis, onRecognition, practice)
            is LearnUiState.Complete -> LessonComplete(state.lesson)
        }
        footer()
    }
}

/** No lesson yet: it appears once messages have been translated (Stitch: Empty Learn). */
@Composable
private fun EmptyLesson(state: LearnUiState.Empty) {
    AlterLinguaCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lesson_empty"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.lrn_no_lesson_today_yet), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = stringResource(R.string.lrn_empty_body, state.language.displayName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.lrn_use_the_alterlingua_keyboard_in),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One card of the lesson (Stitch: Learn / Today's Lesson). */
@Composable
private fun LessonCardContent(
    state: LearnUiState.Card,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    nowMillis: Long,
    onRecognition: (LessonCard, Boolean) -> Unit,
    practice: @Composable (LessonCard) -> Unit,
) {
    val card = state.lesson.current
    TagPill(stringResource(R.string.lrn_item_of, state.index + 1, state.total))

    // Step indicator: one segment per card.
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(state.total) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (i <= state.index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.extendedColors.cardBorder),
            )
        }
    }

    AlterLinguaCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = LessonText.kindLabel(card).string().uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(if (card.kind == CardKind.WORD_CARD) "lesson_word_card" else "lesson_phrase_card"),
            )
            MasteryChip(card.masteryState)
        }
        Text(
            text = card.term,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("lesson_term"),
        )
        if (card.meaning != null) {
            Text(
                text = card.meaning,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("lesson_meaning"),
            )
        } else {
            Text(
                text = stringResource(R.string.lrn_meaning_not_available_right_now),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("lesson_meaning_missing"),
            )
        }

        HorizontalDivider(color = MaterialTheme.extendedColors.cardBorder)

        Text(stringResource(R.string.lrn_where_you_met_it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = LessonText.metParts(card, nowMillis).map { it.string() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("lesson_context"),
        )
        LessonText.helpLine(card)?.let {
            Text(it.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        if (card.context.reasons.isNotEmpty()) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.extendedColors.chipSurface) {
                Text(
                    text = stringResource(R.string.lrn_why_this_item) + card.context.reasons.map { LessonText.reason(it).string() }.joinToString(" · "),
                    modifier = Modifier
                        .padding(12.dp)
                        .testTag("lesson_reasons"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (card.meaning != null) {
        RecallCheck(card, onRecognition)
    }

    practice(card)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onPrevious, enabled = state.canGoBack, modifier = Modifier.weight(1f).testTag("lesson_back")) { Text(stringResource(R.string.action_back)) }
        Button(onClick = onNext, modifier = Modifier.weight(1f).testTag("lesson_next")) {
            Text(stringResource(if (state.isLast) R.string.lrn_finish_lesson else R.string.lrn_next_item))
        }
    }
}

/**
 * "Did you already know this?" (Stitch: Learn / recall check). The one honest way AlterLingua has today to ask the
 * learner to recall a word rather than just repeat it after hearing it, so Adaptive mode has real recognition
 * evidence to work from. A self-report, not a quiz; answering is optional and the question disappears once answered,
 * once per card (going back and returning asks again, matching the daily lesson's own "going back records nothing").
 */
@Composable
private fun RecallCheck(card: LessonCard, onRecognition: (LessonCard, Boolean) -> Unit) {
    var answered by rememberSaveable(card.key) { mutableStateOf(false) }
    if (answered) return
    AlterLinguaCard {
        Column(Modifier.fillMaxWidth().testTag("lesson_recall"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.lrn_recall_prompt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { answered = true; onRecognition(card, true) },
                    modifier = Modifier.weight(1f).testTag("lesson_recall_yes"),
                ) { Text(stringResource(R.string.lrn_recall_yes)) }
                OutlinedButton(
                    onClick = { answered = true; onRecognition(card, false) },
                    modifier = Modifier.weight(1f).testTag("lesson_recall_no"),
                ) { Text(stringResource(R.string.lrn_recall_no)) }
            }
        }
    }
}

/** What shows where practice goes when no practice is provided (previews and screen tests). */
@Composable
private fun PracticePlaceholder() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f).testTag("lesson_listen")) { Text(stringResource(R.string.voice_listen)) }
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f).testTag("lesson_repeat")) { Text(stringResource(R.string.lrn_repeat)) }
    }
}

/** Done for today (Stitch: Lesson Complete). */
@Composable
private fun LessonComplete(lesson: DailyLesson) {
    AlterLinguaCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lesson_complete"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(stringResource(R.string.lrn_done_for_today), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = stringResource(R.string.lrn_complete_body, lesson.cards.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
    lesson.cards.forEach { card ->
        AlterLinguaCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(card.term, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    card.meaning?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                MasteryChip(card.masteryState)
            }
        }
    }
}

private fun previewCard(term: String, type: UnitType, meaning: String?) = LessonCard(
    key = UnitKey("fr", term, type), term = term, kind = if (type == UnitType.WORD) CardKind.WORD_CARD else CardKind.PHRASE_CARD,
    meaning = meaning, meaningLanguage = meaning?.let { "en" },
    context = LessonContext(4, InteractionKind.INCOMING_MESSAGE, System.currentTimeMillis(), 1, listOf(com.alterlingua.app.learning.lessons.LessonReason.ASKED_TRANSLATION, com.alterlingua.app.learning.lessons.LessonReason.USEFUL)),
    masteryState = MasteryStatus.LEARNING,
)

@Preview(showBackground = true)
@Composable
private fun LearnCardPreview() {
    AlterLinguaTheme {
        LearnScreen(
            state = LearnUiState.Card(DailyLesson("2026-09-20", "fr", listOf(previewCard("devis", UnitType.WORD, "quotation"), previewCard("avant midi", UnitType.PHRASE, "before noon")))),
            onPrevious = {}, onNext = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LearnEmptyPreview() {
    AlterLinguaTheme { LearnScreen(state = LearnUiState.Empty(Languages.French), onPrevious = {}, onNext = {}) }
}

/**
 * Standard and Premium, each its own card with its own price and its own Subscribe button (CLAUDE.md section 32):
 * never one merged "Standard & Premium" button. Tapping a card's body always opens the same shared curriculum
 * (CLAUDE.md's own "one curriculum for all plans" decision) — what differs per card is entitlement and price, not
 * content. A plan already owned shows "Open" instead of a price.
 */
@Composable
private fun StandardAndPremiumCards(
    tier: com.alterlingua.app.entitlements.Tier,
    offers: List<com.alterlingua.app.billing.TierOffers>,
    onOpen: () -> Unit,
    onSubscribe: (com.alterlingua.app.billing.SubscriptionOffer) -> Unit,
) {
    PlanEntryCard(
        name = stringResource(R.string.set_plan_standard),
        tagline = stringResource(R.string.set_plan_standard_tagline),
        accent = MaterialTheme.colorScheme.primary,
        accentContainer = MaterialTheme.colorScheme.primaryContainer,
        features = listOf(
            stringResource(R.string.set_plan_standard_feature_everything_free),
            stringResource(R.string.set_plan_standard_feature_full_curriculum),
            stringResource(R.string.set_plan_standard_feature_daily_cap),
            stringResource(R.string.set_plan_standard_feature_image_translation),
        ),
        owned = tier == com.alterlingua.app.entitlements.Tier.STANDARD || tier == com.alterlingua.app.entitlements.Tier.PREMIUM,
        offer = offers.firstOrNull { it.tier == com.alterlingua.app.entitlements.Tier.STANDARD },
        onOpen = onOpen,
        onSubscribe = onSubscribe,
        testTag = "lrn_standard_lessons",
    )
    PlanEntryCard(
        name = stringResource(R.string.set_plan_premium),
        tagline = stringResource(R.string.set_plan_premium_tagline),
        accent = MaterialTheme.colorScheme.tertiary,
        accentContainer = MaterialTheme.colorScheme.tertiaryContainer,
        features = listOf(
            stringResource(R.string.set_plan_premium_feature_everything_standard),
            stringResource(R.string.set_plan_premium_feature_all_languages),
            stringResource(R.string.set_plan_premium_feature_no_cap),
            stringResource(R.string.set_plan_premium_feature_document_translation),
            stringResource(R.string.set_plan_premium_feature_voice_share),
        ),
        owned = tier == com.alterlingua.app.entitlements.Tier.PREMIUM,
        // A Standard subscriber already has full curriculum access: no "trial" framing, just a clear upgrade.
        isUpgrade = tier == com.alterlingua.app.entitlements.Tier.STANDARD,
        offer = offers.firstOrNull { it.tier == com.alterlingua.app.entitlements.Tier.PREMIUM },
        onOpen = onOpen,
        onSubscribe = onSubscribe,
        testTag = "lrn_premium_lessons",
    )
}

/**
 * One tier's own card: a colour accent (Standard = primary, Premium = tertiary, the design system's own existing
 * tokens — no new colours invented) carries through the border, the feature checkmarks and the primary button, so
 * the two plans read as distinct, deliberate products rather than two identical grey boxes.
 */
@Composable
private fun PlanEntryCard(
    name: String,
    tagline: String,
    accent: Color,
    accentContainer: Color,
    features: List<String>,
    owned: Boolean,
    offer: com.alterlingua.app.billing.TierOffers?,
    onOpen: () -> Unit,
    onSubscribe: (com.alterlingua.app.billing.SubscriptionOffer) -> Unit,
    testTag: String,
    /** True when the learner already owns the tier below this one (e.g. Standard looking at Premium): there is
     * nothing to "try" -- the curriculum itself is already unlocked -- so this is a clear upgrade, not a trial. */
    isUpgrade: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = MaterialTheme.shapes.large,
        color = if (owned) accentContainer.copy(alpha = 0.10f) else MaterialTheme.extendedColors.card,
        border = BorderStroke(if (owned) 1.5.dp else 1.dp, if (owned) accent else MaterialTheme.extendedColors.cardBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                if (owned) TagPill(stringResource(R.string.set_plans_current))
            }
            Text(tagline, style = MaterialTheme.typography.titleSmall, color = accent, modifier = Modifier.testTag("${testTag}_tagline"))
            features.forEach { feature ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Text(feature, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (owned) {
                Button(
                    onClick = onOpen,
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    modifier = Modifier.fillMaxWidth().testTag("${testTag}_open"),
                ) { Text(stringResource(R.string.lrn_curriculum_open)) }
            } else if (offer?.available == true) {
                if (isUpgrade) {
                    // Already has full curriculum access via the tier below: this is an upgrade, not a trial.
                    Text(
                        text = stringResource(R.string.lrn_premium_upgrade_cta),
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        modifier = Modifier.testTag("${testTag}_upgrade_label"),
                    )
                } else {
                    // Still within the free trial, this plan's content can be tried before buying it.
                    OutlinedButton(
                        onClick = onOpen,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                        border = BorderStroke(1.dp, accent),
                        modifier = Modifier.fillMaxWidth().testTag("${testTag}_open"),
                    ) { Text(stringResource(R.string.lrn_curriculum_try_it)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    offer.monthly?.let { monthly ->
                        OutlinedButton(
                            onClick = { onSubscribe(monthly) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                            border = BorderStroke(1.dp, accent),
                            modifier = Modifier.weight(1f).testTag("${testTag}_subscribe_monthly"),
                        ) { Text(stringResource(R.string.set_plans_per_month, monthly.formattedPrice)) }
                    }
                    offer.annual?.let { annual ->
                        Button(
                            onClick = { onSubscribe(annual) },
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            modifier = Modifier.weight(1f).testTag("${testTag}_subscribe_annual"),
                        ) { Text(stringResource(R.string.set_plans_per_year, annual.formattedPrice)) }
                    }
                }
            } else {
                if (isUpgrade) {
                    Text(
                        text = stringResource(R.string.lrn_premium_upgrade_cta),
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        modifier = Modifier.testTag("${testTag}_upgrade_label"),
                    )
                } else {
                    OutlinedButton(
                        onClick = onOpen,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                        border = BorderStroke(1.dp, accent),
                        modifier = Modifier.fillMaxWidth().testTag("${testTag}_open"),
                    ) { Text(stringResource(R.string.lrn_curriculum_try_it)) }
                }
                TagPill(stringResource(R.string.set_plans_not_available_yet))
            }
        }
    }
}
