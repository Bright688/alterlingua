package com.alterlingua.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLinksTest {

    @Test
    fun everyTabCanBeNamedByItsRoute() {
        for (destination in TopLevelDestination.entries) {
            assertEquals(destination, AppLinks.destinationForRoute(destination.route))
        }
    }

    @Test
    fun theSettingsButtonOpensTheSettingsTab() {
        assertEquals(TopLevelDestination.SETTINGS, AppLinks.destinationForRoute(TopLevelDestination.SETTINGS.route))
    }

    @Test
    fun unknownOrMissingRoutesAreIgnored() {
        assertNull(AppLinks.destinationForRoute(null))
        assertNull(AppLinks.destinationForRoute(""))
        assertNull(AppLinks.destinationForRoute("payments"))
    }
}
