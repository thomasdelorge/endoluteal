package fr.luteal.core.openrouter

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioTranscriptionPlanTest {
    @Test
    fun shortFileSingleSegment() {
        val plan = AudioTranscriptionPlan.plan(1_000_000, 60_000)
        assertEquals(1, plan.chunkCount)
    }

    @Test
    fun largeFileMultipleSegments() {
        val plan = AudioTranscriptionPlan.plan(50L * 1024 * 1024, 3_600_000)
        assert(plan.chunkCount > 1)
    }

    @Test
    fun longDurationMultipleSegments() {
        val plan = AudioTranscriptionPlan.plan(5_000_000, 3_600_000)
        assert(plan.chunkCount >= 8)
    }
}
