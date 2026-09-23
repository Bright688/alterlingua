package com.alterlingua.app.ui.onboarding

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alterlingua.app.R
import androidx.compose.ui.res.stringResource
import com.alterlingua.app.learning.AssistanceMode
import com.alterlingua.app.learning.LanguageLevel
import com.alterlingua.app.learning.LearningPurpose
import com.alterlingua.app.ui.components.pathIcon
import com.alterlingua.app.ui.theme.extendedColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Icons drawn from Material Symbols path data (same approach as the tab icons). */
internal object OnboardingIcons {
    val Translate: ImageVector by lazy {
        pathIcon(
            "Translate",
            "M12.87,15.07l-2.54,-2.51 0.03,-0.03c1.74,-1.94 2.98,-4.17 3.71,-6.53H17V4h-7V2H8v2H1v1.99h11.17C11.5,7.92 10.44,9.75 9,11.35 8.07,10.32 7.3,9.19 6.69,8h-2c0.73,1.63 1.73,3.17 2.98,4.56l-5.09,5.02L4,19l5,-5 3.11,3.11 0.76,-2.04zM18.5,10h-2L12,22h2l1.12,-3h4.75L21,22h2l-4.5,-12zM15.88,17l1.62,-4.33L19.12,17h-3.24z",
        )
    }
    val Work: ImageVector by lazy {
        pathIcon(
            "Work",
            "M20,6h-4V4c0,-1.11 -0.89,-2 -2,-2h-4c-1.11,0 -2,0.89 -2,2v2H4c-1.11,0 -1.99,0.89 -1.99,2L2,19c0,1.11 0.89,2 2,2h16c1.11,0 2,-0.89 2,-2V8c0,-1.11 -0.89,-2 -2,-2zM10,4h4v2h-4V4z",
        )
    }
    val Flight: ImageVector by lazy {
        pathIcon(
            "Flight",
            "M21,16v-2l-8,-5V3.5c0,-0.83 -0.67,-1.5 -1.5,-1.5S10,2.67 10,3.5V9l-8,5v2l8,-2.5V19l-2,1.5V22l3.5,-1 3.5,1v-1.5L13,19v-5.5l8,2.5z",
        )
    }
    val TrendingUp: ImageVector by lazy {
        pathIcon("TrendingUp", "M16,6l2.29,2.29 -4.88,4.88 -4,-4L2,16.59 3.41,18l6,-6 4,4 6.3,-6.29L22,12V6z")
    }
    val TouchApp: ImageVector by lazy {
        pathIcon(
            "TouchApp",
            "M9,11.24V7.5C9,6.12 10.12,5 11.5,5S14,6.12 14,7.5v3.74c1.21,-0.81 2,-2.18 2,-3.74C16,5.01 13.99,3 11.5,3S7,5.01 7,7.5c0,1.56 0.79,2.93 2,3.74zM18.84,15.87l-4.54,-2.26c-0.17,-0.07 -0.35,-0.11 -0.54,-0.11H13v-6c0,-0.83 -0.67,-1.5 -1.5,-1.5S10,6.67 10,7.5v10.74l-3.43,-0.72c-0.08,-0.01 -0.15,-0.03 -0.24,-0.03 -0.31,0 -0.59,0.13 -0.79,0.33l-0.79,0.8 4.94,4.94c0.27,0.27 0.65,0.44 1.06,0.44h6.79c0.75,0 1.33,-0.55 1.44,-1.28l0.75,-5.27c0.01,-0.07 0.02,-0.14 0.02,-0.2 0,-0.62 -0.38,-1.16 -0.91,-1.38z",
        )
    }
}

// ---- Text for the saved choices (also used on the final summary) ----

@Composable
internal fun LearningPurpose.label(): String = stringResource(
    when (this) {
        LearningPurpose.WORK -> R.string.purpose_work
        LearningPurpose.TRAVEL -> R.string.purpose_travel
        LearningPurpose.FAMILY -> R.string.purpose_family
        LearningPurpose.STUDY -> R.string.purpose_study
    },
)

@Composable
internal fun LanguageLevel.label(): String = stringResource(
    when (this) {
        LanguageLevel.BEGINNER -> R.string.level_beginner
        LanguageLevel.SOME_BASICS -> R.string.level_some_basics
        LanguageLevel.INTERMEDIATE -> R.string.level_intermediate
    },
)

@Composable
internal fun LanguageLevel.description(): String = stringResource(
    when (this) {
        LanguageLevel.BEGINNER -> R.string.level_beginner_desc
        LanguageLevel.SOME_BASICS -> R.string.level_some_basics_desc
        LanguageLevel.INTERMEDIATE -> R.string.level_intermediate_desc
    },
)

@Composable
internal fun AssistanceMode.label(): String = stringResource(
    when (this) {
        AssistanceMode.FULL_SUPPORT -> R.string.mode_full_support
        AssistanceMode.ADAPTIVE -> R.string.mode_adaptive
        AssistanceMode.ON_DEMAND -> R.string.mode_on_demand
    },
)

// ---- Time ----

/** "08:00 PM", or "20:00" on phones set to 24-hour time. */
fun formatTime(time: LocalTime, is24Hour: Boolean): String =
    time.format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "hh:mm a", Locale.getDefault()))

@Composable
fun rememberFormattedTime(time: LocalTime): String {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(time, is24Hour) { formatTime(time, is24Hour) }
}

// ---- Building blocks ----

/** Back button, "STEP N OF 5", the step name, and a segmented progress bar. */
@Composable
internal fun OnboardingHeader(
    stepNumber: Int,
    stepCount: Int,
    stepTitle: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (canGoBack) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("onboarding_back"),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.extendedColors.chipSurface,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.onb_step_of, stepNumber, stepCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stepTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(48.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(stepCount) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < stepNumber) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.extendedColors.cardBorder
                            },
                        ),
                )
            }
        }
    }
}

/** Big rounded button at the bottom of each step. */
@Composable
internal fun ContinueButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.AutoMirrored.Filled.ArrowForward,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("onboarding_continue"),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

/** Purpose choice: a rounded chip with an icon. One of four is selected. */
@Composable
internal fun PurposeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.extendedColors.chipSurface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Level choice: a card with a title, a description and a radio button on the right. */
@Composable
internal fun LevelCard(
    level: LanguageLevel,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.extendedColors.card,
        contentColor = contentColor,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(level.label(), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = level.description(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.85f),
                )
            }
            RadioMark(selected = selected, color = contentColor)
        }
    }
}

/** A typing-style choice: a card with the style's name, a one-line description and a radio button on the right. */
@Composable
internal fun StyleCard(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.extendedColors.card,
        contentColor = contentColor,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(text = description, style = MaterialTheme.typography.bodyMedium, color = contentColor.copy(alpha = 0.85f))
            }
            RadioMark(selected = selected, color = contentColor)
        }
    }
}

/** Round radio mark (ring, with a dot when selected). */
@Composable
private fun RadioMark(selected: Boolean, color: Color) {
    Canvas(Modifier.size(26.dp)) {
        drawCircle(
            color = color,
            radius = size.minDimension / 2 - 1.dp.toPx(),
            style = Stroke(width = 2.dp.toPx()),
        )
        if (selected) drawCircle(color = color, radius = size.minDimension / 4)
    }
}

/** Assistance mode choice. The chosen card shows "Active selection". */
@Composable
internal fun ModeCard(
    icon: ImageVector,
    title: String,
    tag: String,
    tagHighlighted: Boolean,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.extendedColors.card,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.extendedColors.cardBorder,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.extendedColors.navIndicator),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (tagHighlighted) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            text = tag,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                } else {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (selected) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.onb_active_selection),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Small note with an icon on a tinted background. */
@Composable
internal fun NoteRow(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.extendedColors.chipSurface,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Numbered dot used in the "what you will review" list. */
@Composable
internal fun NumberDot(number: Int) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun StatusChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
