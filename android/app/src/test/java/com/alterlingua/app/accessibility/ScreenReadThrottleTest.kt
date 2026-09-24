package com.alterlingua.app.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenReadThrottleTest {

    @Test
    fun theFirstCall_isAlwaysAllowed() {
        val throttle = ScreenReadThrottle(minIntervalMillis = 2_000, clock = { 10_000 })
        assertTrue(throttle.tryAcquire())
    }

    @Test
    fun aFloodOfCallsWithinTheWindow_isCollapsedToOne() {
        var now = 0L
        val throttle = ScreenReadThrottle(minIntervalMillis = 2_000, clock = { now })
        assertTrue(throttle.tryAcquire())
        // A real incident: several calls a second, sustained. Every one still inside the window must be refused.
        repeat(14) { // 14 * 130ms = 1,820ms, still under the 2,000ms window
            now += 130
            assertFalse("call at t=$now", throttle.tryAcquire())
        }
    }

    @Test
    fun onceTheIntervalHasPassed_theNextCallIsAllowedAgain() {
        var now = 0L
        val throttle = ScreenReadThrottle(minIntervalMillis = 2_000, clock = { now })
        assertTrue(throttle.tryAcquire())
        now += 1_999
        assertFalse(throttle.tryAcquire())
        now += 1
        assertTrue(throttle.tryAcquire())
    }

    @Test
    fun aContinuouslyChangingScreen_isStillReadAtTheBoundedRate_neverFaster() {
        var now = 0L
        val throttle = ScreenReadThrottle(minIntervalMillis = 1_000, clock = { now })
        var allowed = 0
        // Ten seconds of events every 100ms (the flooding pattern), bounded to once a second.
        for (tick in 1..100) {
            now = tick * 100L
            if (throttle.tryAcquire()) allowed++
        }
        assertTrue("expected roughly 10 allowed reads over 10s, got $allowed", allowed in 9..11)
    }

    @Test
    fun defaultInterval_isConservativeEnoughForATightProviderBudget() {
        // Documents the real incident's fix: 2s between reads bounds worst case to 30 live-chat translate
        // attempts a minute from this feature alone, not several hundred.
        val throttle = ScreenReadThrottle()
        var now = 0L
        var allowed = 0
        for (tick in 1..600) { // one minute of events every 100ms
            now = tick * 100L
            if (throttle.tryAcquire()) allowed++
        }
        assertTrue("expected at most ~30 allowed reads per minute, got $allowed", allowed <= 31)
    }
}
