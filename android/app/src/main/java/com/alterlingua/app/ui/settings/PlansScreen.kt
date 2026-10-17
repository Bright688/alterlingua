package com.alterlingua.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alterlingua.app.R
import com.alterlingua.app.billing.SubscriptionOffer
import com.alterlingua.app.billing.TierOffers
import com.alterlingua.app.entitlements.Tier
import com.alterlingua.app.ui.components.ScreenFrame
import com.alterlingua.app.ui.components.TagPill
import com.alterlingua.app.ui.theme.extendedColors

/**
 * Settings → Plan (CLAUDE.md section 33): what FREE, Standard and Premium each actually unlock today, so the three
 * plans are genuinely visible rather than only implied by a trial limit. Only real, already-built differences are
 * listed — nothing here promises a feature the app does not yet gate. Standard and Premium are two separate Google
 * Play subscriptions, each with its own monthly/annual price from [offers] — never a fixed string, since only Play
 * Console knows the real price. [onSubscribe] launches Google Play's own purchase sheet for that exact offer.
 */
@Composable
fun PlansScreen(
    tier: Tier,
    modifier: Modifier = Modifier,
    offers: List<TierOffers> = emptyList(),
    onSubscribe: (SubscriptionOffer) -> Unit = {},
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
            accent = MaterialTheme.colorScheme.outline,
            name = stringResource(R.string.set_plan_free_trial),
            staticPrice = stringResource(R.string.set_plan_free_price),
            features = listOf(
                stringResource(R.string.set_plan_free_feature_keyboard),
                stringResource(R.string.set_plan_free_feature_micro_lessons),
                stringResource(R.string.set_plan_free_feature_trial),
                stringResource(R.string.set_plan_free_feature_voice_trial),
            ),
            offer = null,
            onSubscribe = onSubscribe,
        )
        PlanCard(
            tier = Tier.STANDARD,
            current = tier == Tier.STANDARD,
            accent = MaterialTheme.colorScheme.primary,
            name = stringResource(R.string.set_plan_standard),
            tagline = stringResource(R.string.set_plan_standard_tagline),
            staticPrice = null,
            features = listOf(
                stringResource(R.string.set_plan_standard_feature_everything_free),
                stringResource(R.string.set_plan_standard_feature_full_curriculum),
                stringResource(R.string.set_plan_standard_feature_daily_cap),
                stringResource(R.string.set_plan_standard_feature_image_translation),
                stringResource(R.string.set_plan_standard_feature_voice_share),
            ),
            offer = offers.firstOrNull { it.tier == Tier.STANDARD } ?: TierOffers(Tier.STANDARD, null, null),
            onSubscribe = onSubscribe,
        )
        PlanCard(
            tier = Tier.PREMIUM,
            current = tier == Tier.PREMIUM,
            accent = MaterialTheme.colorScheme.tertiary,
            // Already a Standard subscriber: nothing to "try", this is a clear upgrade.
            isUpgrade = tier == Tier.STANDARD,
            name = stringResource(R.string.set_plan_premium),
            tagline = stringResource(R.string.set_plan_premium_tagline),
            staticPrice = null,
            features = listOf(
                stringResource(R.string.set_plan_premium_feature_everything_standard),
                stringResource(R.string.set_plan_premium_feature_all_languages),
                stringResource(R.string.set_plan_premium_feature_no_cap),
                stringResource(R.string.set_plan_premium_feature_document_translation),
                stringResource(R.string.set_plan_premium_feature_voice_share),
            ),
            offer = offers.firstOrNull { it.tier == Tier.PREMIUM } ?: TierOffers(Tier.PREMIUM, null, null),
            onSubscribe = onSubscribe,
        )
        footer()
    }
}

/**
 * One tier's own card: a colour accent (Free = neutral outline, Standard = primary, Premium = tertiary -- the
 * design system's own existing tokens) carries through the border, the feature checkmarks and the primary button,
 * matching the same treatment the Learn tab's own Standard/Premium cards use, so the plan feels like the same
 * deliberate product wherever it is shown.
 */
@Composable
private fun PlanCard(
    tier: Tier,
    current: Boolean,
    accent: Color,
    name: String,
    staticPrice: String?,
    features: List<String>,
    offer: TierOffers?,
    onSubscribe: (SubscriptionOffer) -> Unit,
    /** A short benefit-led pitch shown under the plan name (null for Free, which has [staticPrice] instead). */
    tagline: String? = null,
    /** True when the learner already owns the tier below this one: nothing to "try", just a clear upgrade. */
    isUpgrade: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("plan_card_${tier.name.lowercase()}"),
        shape = MaterialTheme.shapes.large,
        color = if (current) accent.copy(alpha = 0.10f) else MaterialTheme.extendedColors.card,
        border = BorderStroke(if (current) 1.5.dp else 1.dp, if (current) accent else MaterialTheme.extendedColors.cardBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                if (current) TagPill(stringResource(R.string.set_plans_current), modifier = Modifier.testTag("plan_current_${tier.name.lowercase()}"))
            }
            if (staticPrice != null) {
                Text(staticPrice, style = MaterialTheme.typography.titleMedium, color = accent)
            }
            if (tagline != null) {
                Text(tagline, style = MaterialTheme.typography.titleSmall, color = accent, modifier = Modifier.testTag("plan_tagline_${tier.name.lowercase()}"))
            }
            HorizontalDivider(color = MaterialTheme.extendedColors.cardBorder)
            features.forEach { feature ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = accent)
                    Text(feature, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (offer != null && !current) {
                if (offer.available) {
                    if (isUpgrade) {
                        Text(
                            text = stringResource(R.string.lrn_premium_upgrade_cta),
                            style = MaterialTheme.typography.labelLarge,
                            color = accent,
                            modifier = Modifier.testTag("plan_upgrade_${tier.name.lowercase()}"),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        offer.monthly?.let { monthly ->
                            OutlinedButton(
                                onClick = { onSubscribe(monthly) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                                border = BorderStroke(1.dp, accent),
                                modifier = Modifier.weight(1f).testTag("plan_subscribe_${tier.name.lowercase()}_monthly"),
                            ) { Text(stringResource(R.string.set_plans_per_month, monthly.formattedPrice)) }
                        }
                        offer.annual?.let { annual ->
                            Button(
                                onClick = { onSubscribe(annual) },
                                colors = ButtonDefaults.buttonColors(containerColor = accent),
                                modifier = Modifier.weight(1f).testTag("plan_subscribe_${tier.name.lowercase()}_annual"),
                            ) { Text(stringResource(R.string.set_plans_per_year, annual.formattedPrice)) }
                        }
                    }
                } else {
                    if (isUpgrade) {
                        Text(
                            text = stringResource(R.string.lrn_premium_upgrade_cta),
                            style = MaterialTheme.typography.labelLarge,
                            color = accent,
                            modifier = Modifier.testTag("plan_upgrade_${tier.name.lowercase()}"),
                        )
                    }
                    TagPill(
                        stringResource(R.string.set_plans_not_available_yet),
                        modifier = Modifier.testTag("plan_unavailable_${tier.name.lowercase()}"),
                    )
                }
            }
        }
    }
}
