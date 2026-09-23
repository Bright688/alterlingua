package com.alterlingua.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun hasTheFiveMainAreas_inBottomBarOrder() {
        assertEquals(
            listOf("home", "learn", "words", "progress", "settings"),
            TopLevelDestination.entries.map { it.route },
        )
    }

    @Test
    fun routesAreUnique() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun startsAtHome() {
        assertEquals(TopLevelDestination.HOME, TopLevelDestination.entries.first())
    }
}
