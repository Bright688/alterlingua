package com.alterlingua.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alterlingua.app.billing.BillingPeriod
import com.alterlingua.app.billing.SubscriptionOffer
import com.alterlingua.app.billing.TierOffers
import com.alterlingua.app.entitlements.Tier
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private fun offerFor(tier: Tier) = TierOffers(
    tier = tier,
    monthly = SubscriptionOffer("${tier.name.lowercase()}_monthly", tier, BillingPeriod.MONTHLY, "$3.99", "monthly_token"),
    annual = SubscriptionOffer("${tier.name.lowercase()}_annual", tier, BillingPeriod.ANNUAL, "$39.99", "annual_token"),
)

/** Runs on a device or emulator. */
class PlansScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun allThreePlans_areShown_withTheCurrentOneMarked() {
        composeTestRule.setContent { AlterLinguaTheme { PlansScreen(tier = Tier.FREE, onSubscribe = {}) } }
        composeTestRule.onNodeWithTag("plan_card_free").assertIsDisplayed()
        composeTestRule.onNodeWithTag("plan_card_standard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("plan_card_premium").assertIsDisplayed()
        composeTestRule.onNodeWithTag("plan_current_free").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("plan_current_standard").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("plan_current_premium").assertCountEquals(0)
    }

    @Test
    fun theCurrentPlan_offersNoSubscribeButtons_theOthersDo() {
        val offers = listOf(offerFor(Tier.STANDARD), offerFor(Tier.PREMIUM))
        composeTestRule.setContent { AlterLinguaTheme { PlansScreen(tier = Tier.STANDARD, offers = offers, onSubscribe = {}) } }
        composeTestRule.onAllNodesWithTag("plan_subscribe_standard_monthly").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("plan_subscribe_standard_annual").assertCountEquals(0)
        composeTestRule.onNodeWithTag("plan_subscribe_premium_monthly").assertIsDisplayed()
        composeTestRule.onNodeWithTag("plan_subscribe_premium_annual").assertIsDisplayed()
    }

    @Test
    fun subscribingToStandardOrPremium_callsOnSubscribeWithThatExactOffer() {
        val offers = listOf(offerFor(Tier.STANDARD), offerFor(Tier.PREMIUM))
        val subscribed = mutableListOf<SubscriptionOffer>()
        composeTestRule.setContent { AlterLinguaTheme { PlansScreen(tier = Tier.FREE, offers = offers, onSubscribe = { subscribed += it }) } }
        composeTestRule.onNodeWithTag("plan_subscribe_standard_monthly").performClick()
        composeTestRule.onNodeWithTag("plan_subscribe_premium_annual").performClick()
        assertEquals(listOf("standard_monthly", "premium_annual"), subscribed.map { it.productId })
    }

    @Test
    fun whenNoOfferExistsYetInPlayConsole_itSaysSoInsteadOfOfferingToBuy() {
        composeTestRule.setContent { AlterLinguaTheme { PlansScreen(tier = Tier.FREE, offers = emptyList(), onSubscribe = {}) } }
        composeTestRule.onNodeWithTag("plan_unavailable_standard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("plan_unavailable_premium").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("plan_subscribe_standard_monthly").assertCountEquals(0)
    }
}
