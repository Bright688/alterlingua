package com.alterlingua.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.alterlingua.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.alterlingua.app.learning.LanguageMapSummary
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.ui.theme.extendedColors

/**
 * Common frame for a tab screen: top bar with the logo and title, then scrolling content
 * with the design system's 16dp margin and 16dp gap between cards.
 */
@Composable
fun ScreenFrame(
    title: String,
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(testTag)
            .verticalScroll(rememberScrollState()),
    ) {
        AppTopBar(title)
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * The AlterLingua mark: two overlapping speech bubbles (one language answering another) on a rounded blue tile. Drawn in code,
 * so it is sharp at any size and follows the theme; decorative (the screen title beside it is what screen readers read).
 */
@Composable
fun AlterLinguaMark(modifier: Modifier = Modifier) {
    val tile = MaterialTheme.colorScheme.primaryContainer
    val tileEdge = MaterialTheme.colorScheme.primary
    val back = MaterialTheme.colorScheme.secondaryContainer
    val front = Color.White
    val lines = MaterialTheme.colorScheme.primaryContainer
    Canvas(modifier = modifier.clip(MaterialTheme.shapes.medium)) {
        val w = size.width
        val h = size.height
        drawRect(Brush.linearGradient(listOf(tile, tileEdge), start = Offset(0f, 0f), end = Offset(w, h)))
        val corner = androidx.compose.ui.geometry.CornerRadius(w * 0.09f)

        // Back bubble (upper right) with its tail at the lower right.
        drawRoundRect(back, topLeft = Offset(w * 0.40f, h * 0.15f), size = androidx.compose.ui.geometry.Size(w * 0.46f, h * 0.34f), cornerRadius = corner)
        drawPath(
            Path().apply {
                moveTo(w * 0.70f, h * 0.49f)
                lineTo(w * 0.80f, h * 0.49f)
                lineTo(w * 0.80f, h * 0.60f)
                close()
            },
            back,
        )

        // Front bubble (lower left) with its tail at the lower left, and two lines of "text".
        drawRoundRect(front, topLeft = Offset(w * 0.14f, h * 0.34f), size = androidx.compose.ui.geometry.Size(w * 0.52f, h * 0.36f), cornerRadius = corner)
        drawPath(
            Path().apply {
                moveTo(w * 0.22f, h * 0.70f)
                lineTo(w * 0.34f, h * 0.70f)
                lineTo(w * 0.22f, h * 0.83f)
                close()
            },
            front,
        )
        val stroke = h * 0.045f
        drawLine(lines, Offset(w * 0.22f, h * 0.46f), Offset(w * 0.56f, h * 0.46f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(lines, Offset(w * 0.22f, h * 0.57f), Offset(w * 0.44f, h * 0.57f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** Logo tile and screen title (Stitch top app bar). */
@Composable
fun AppTopBar(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlterLinguaMark(Modifier.size(40.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** White card with a hairline border (Stitch "Level 1" surface). */
@Composable
fun AlterLinguaCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.extendedColors.card,
        border = BorderStroke(1.dp, MaterialTheme.extendedColors.cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Small rounded label, used for tags such as "Daily micro-lesson". */
@Composable
fun TagPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.extendedColors.chipSurface,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** UI label for a learning state. UNKNOWN is shown as "New" (Stitch wording). */
@Composable
fun MasteryStatus.label(): String = stringResource(
    when (this) {
        MasteryStatus.UNKNOWN -> R.string.mastery_new
        MasteryStatus.LEARNING -> R.string.mastery_learning
        MasteryStatus.FAMILIAR -> R.string.mastery_familiar
        MasteryStatus.MASTERED -> R.string.mastery_mastered
    },
)

/** Status chip for a Personal Language Map word (Stitch "Hierarchy Chips"). */
@Composable
fun MasteryChip(status: MasteryStatus, modifier: Modifier = Modifier) {
    val palette = MaterialTheme.extendedColors.mastery(status)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = palette.container,
        border = BorderStroke(1.dp, palette.border),
    ) {
        Text(
            text = status.label(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = palette.content,
        )
    }
}

/** Stacked bar showing how the words are split across the four learning states. */
@Composable
fun LanguageMapBar(summary: LanguageMapSummary, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape),
    ) {
        MasteryStatus.entries.forEach { status ->
            val count = summary.countOf(status)
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .weight(count.toFloat())
                        .fillMaxSize()
                        .background(MaterialTheme.extendedColors.mastery(status).accent),
                )
            }
        }
    }
}

/**
 * Smooth line chart of "how often translation help was still needed", oldest value first.
 * Values are percentages from 0 to 100.
 */
@Composable
fun DependenceChart(
    values: List<Int>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 120.dp,
) {
    val line = MaterialTheme.colorScheme.primaryContainer
    val guide = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (values.size < 2) return@Canvas
        val pad = 8.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        val points = values.mapIndexed { i, v ->
            Offset(
                x = pad + w * i / (values.size - 1),
                y = pad + h * (1f - v.coerceIn(0, 100) / 100f),
            )
        }

        // Guide lines at 0% and 100%.
        drawLine(guide, Offset(pad, pad), Offset(pad + w, pad), strokeWidth = 1.dp.toPx())
        drawLine(guide, Offset(pad, pad + h), Offset(pad + w, pad + h), strokeWidth = 1.dp.toPx())

        // Smooth curve through the points using horizontal control handles.
        val curve = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val cur = points[i]
                val mid = (prev.x + cur.x) / 2f
                cubicTo(mid, prev.y, mid, cur.y, cur.x, cur.y)
            }
        }
        val area = Path().apply {
            addPath(curve)
            lineTo(points.last().x, pad + h)
            lineTo(points.first().x, pad + h)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(
                listOf(line.copy(alpha = 0.18f), Color.Transparent),
                startY = pad,
                endY = pad + h,
            ),
        )
        drawPath(curve, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        points.forEach { drawCircle(line, radius = 4.dp.toPx(), center = it) }
    }
}

/** Card showing one big number with a caption. */
@Composable
fun MetricTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    AlterLinguaCard(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
