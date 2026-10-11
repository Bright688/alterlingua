package com.alterlingua.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alterlingua.app.R
import com.alterlingua.app.entitlements.Tier
import com.alterlingua.app.ui.components.AlterLinguaCard
import com.alterlingua.app.ui.components.TagPill
import com.alterlingua.app.ui.components.ScreenFrame
import com.alterlingua.app.ui.theme.extendedColors

/**
 * Settings → Plan (CLAUDE.md section 33): what FREE, Standard and Premium each actually unlock today, so the three
 * plans are genuinely visible rather than only implied by a trial limit. Only real, already-built differences are
 * listed — nothing here promises a feature the app does not yet gate. [onSubscribe] opens the "Coming soon"
 * placeholder (CLAUDE.md sections 31-32: real Google Play Billing is a later milestone).
 */
@Composable
fun PlansScreen(
    tier: Tier,
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    ScreenFrame(
        title = stringResource(R.string.set_plans_title),
        testTag = "screen_plans",
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.set_plans_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PlanCard(
            tier = Tier.FREE,
            current = tier == Tier.FREE,
            name = stringResource(R.string.set_plan_free_trial),
            price = stringResource(R.string.set_plan_free_price),
            features = listOf(
                stringResource(R.string.set_plan_free_feature_keyboard),
                stringResource(R.string.set_plan_free_feature_micro_lessons),
                stringResource(R.string.set_plan_free_feature_trial),
            ),
            onSubscribe = null,
        )
        PlanCard(
            tier = Tier.STANDARD,
            current = tier == Tier.STANDARD,
            name = stringResource(R.string.set_plan_standard),
            price = stringResource(R.string.set_plan_standard_price),
            features = listOf(
                stringResource(R.string.set_plan_standard_feature_everything_free),
                stringResource(R.string.set_plan_standard_feature_full_curriculum),
                stringResource(R.string.set_plan_standard_feature_daily_cap),
            ),
            onSubscribe = onSubscribe.takeIf { tier != Tier.STANDARD },
        )
        PlanCard(
            tier = Tier.PREMIUM,
            current = tier == Tier.PREMIUM,
            name = stringResource(R.string.set_plan_premium),
            price = stringResource(R.string.set_plan_premium_price),
            features = listOf(
                stringResource(R.string.set_plan_premium_feature_everything_standard),
                stringResource(R.string.set_plan_premium_feature_all_languages),
                stringResource(R.string.set_plan_premium_feature_no_cap),
            ),
            onSubscribe = onSubscribe.takeIf { tier != Tier.PREMIUM },
        )
        footer()
    }
}

@Composable
private fun PlanCard(
    tier: Tier,
    current: Boolean,
    name: String,
    price: String,
    features: List<String>,
    onSubscribe: (() -> Unit)?,
) {
    AlterLinguaCard(modifier = Modifier.testTag("plan_card_${tier.name.lowercase()}")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            if (current) TagPill(stringResource(R.string.set_plans_current), modifier = Modifier.testTag("plan_current_${tier.name.lowercase()}"))
        }
        Text(price, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(color = MaterialTheme.extendedColors.cardBorder)
        features.forEach { feature ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(feature, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (onSubscribe != null) {
            Button(onClick = onSubscribe, modifier = Modifier.fillMaxWidth().testTag("plan_subscribe_${tier.name.lowercase()}")) {
                Text(stringResource(R.string.set_plans_subscribe))
            }
        }
    }
}
