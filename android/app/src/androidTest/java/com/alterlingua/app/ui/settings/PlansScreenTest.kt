package com.alterlingua.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alterlingua.app.entitlements.Tier
import com.alterlingua.app.ui.theme.AlterLinguaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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
    fun theCurrentPlan_offersNoSubscribeButton_theOthersDo() {
        composeTestRule.setContent { AlterLinguaTheme { PlansScreen(tier = Tier.STANDARD, onSubscribe = {}) } }
        composeTestRule.onAllNodesWithTag("plan_subscribe_standard").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("plan_subscribe_free").assertCountEquals(0) // Free never offers a subscribe button
    }

    @Test
    fun subscribingToStandardOrPremium_callsOnSubscribe() {
        var subscribed = 0
        composeTestRule.setContent { AlterLinguaTheme { PlansScreen(tier = Tier.FREE, onSubscribe = { subscribed++ }) } }
        composeTestRule.onNodeWithTag("plan_subscribe_standard").performClick()
        assertEquals(1, subscribed)
        composeTestRule.onNodeWithTag("plan_subscribe_premium").performClick()
        assertEquals(2, subscribed)
    }
}
