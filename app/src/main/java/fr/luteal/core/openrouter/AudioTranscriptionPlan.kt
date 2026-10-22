package fr.luteal.core.openrouter

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object AudioTranscriptionLimits {
    const val CHUNK_MAX_BYTES: Long = 20L * 1024 * 1024
    const val CHUNK_MAX_DURATION_SEC: Long = 480
}

data class AudioTranscriptionPlan(
    val chunkDurationSec: Long,
    val chunkCount: Int
) {
    companion object {
        fun plan(sizeBytes: Long, durationMs: Long?): AudioTranscriptionPlan {
            val durationSec = durationMs?.let { max(1L, it / 1000) }
            val bytesPerSec = if (durationSec != null && durationSec > 0) {
                sizeBytes.toDouble() / durationSec.toDouble()
            } else {
                null
            }
            val maxDurationFromBytes = bytesPerSec?.let { bps ->
                if (bps <= 0) AudioTranscriptionLimits.CHUNK_MAX_DURATION_SEC
                else min(
                    AudioTranscriptionLimits.CHUNK_MAX_DURATION_SEC,
                    (AudioTranscriptionLimits.CHUNK_MAX_BYTES / bps).toLong().coerceAtLeast(1L)
                )
            } ?: AudioTranscriptionLimits.CHUNK_MAX_DURATION_SEC

            val needsChunkingBySize = sizeBytes > AudioTranscriptionLimits.CHUNK_MAX_BYTES
            val needsChunkingByDuration = durationSec != null &&
                durationSec > AudioTranscriptionLimits.CHUNK_MAX_DURATION_SEC

            if (!needsChunkingBySize && !needsChunkingByDuration) {
                return AudioTranscriptionPlan(
                    chunkDurationSec = durationSec ?: AudioTranscriptionLimits.CHUNK_MAX_DURATION_SEC,
                    chunkCount = 1
                )
            }

            val chunkDuration = max(1L, min(maxDurationFromBytes, AudioTranscriptionLimits.CHUNK_MAX_DURATION_SEC))
            val totalSec = durationSec ?: chunkDuration
            val count = max(1, ceil(totalSec.toDouble() / chunkDuration.toDouble()).toInt())
            return AudioTranscriptionPlan(chunkDurationSec = chunkDuration, chunkCount = count)
        }
    }
}
