package com.alterlingua.app.accessibility

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.currentTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadSchedulerTest {

    @Test
    fun aFloodOfContentChanges_becomesOneReadingPerWindow() = runTest {
        var reads = 0
        val scheduler = ReadScheduler(this, delayMillis = 350) { reads++ }
        // 200 events over two seconds (100 per second): the flood that used to hit the backend.
        repeat(200) {
            scheduler.pokeAtMostEvery()
            advanceTimeBy(10)
        }
        advanceTimeBy(400)
        runCurrent()
        // ~2.0 s of events / 350 ms window = 6 readings, plus at most the trailing one.
        assertTrue("expected about 6 readings, got $reads", reads in 5..8)
    }

    @Test
    fun aSteadyStreamOfEvents_cannotStarveTheReading() = runTest {
        var reads = 0
        val scheduler = ReadScheduler(this, delayMillis = 350) { reads++ }
        repeat(100) {
            scheduler.pokeAtMostEvery()
            advanceTimeBy(20)
        }
        assertTrue(reads >= 1)
        scheduler.cancel()
    }

    @Test
    fun whileTheScreenIsMoving_theReadingWaitsUntilItSettles() = runTest {
        var reads = 0
        val scheduler = ReadScheduler(this, delayMillis = 300) { reads++ }
        repeat(40) {
            scheduler.pokeWhenSettled()
            advanceTimeBy(50) // a fling: events every 50 ms for two seconds
        }
        assertEquals("no reading while it is still moving", 0, reads)
        advanceTimeBy(301)
        runCurrent()
        assertEquals(1, reads)
    }

    @Test
    fun cancelling_dropsAPendingReading() = runTest {
        var reads = 0
        val scheduler = ReadScheduler(this, delayMillis = 300) { reads++ }
        scheduler.pokeAtMostEvery()
        scheduler.cancel()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, reads)
    }

    @Test
    fun aPokeDuringAReading_schedulesTheNextOne() = runTest {
        var reads = 0
        lateinit var scheduler: ReadScheduler
        scheduler = ReadScheduler(this, delayMillis = 100) {
            reads++
            if (reads == 1) scheduler.pokeAtMostEvery()
        }
        scheduler.pokeAtMostEvery()
        advanceTimeBy(101)
        runCurrent()
        assertEquals(1, reads)
        advanceTimeBy(101)
        runCurrent()
        assertEquals(2, reads)
        assertEquals(true, currentTime >= 200)
    }
}
