package fr.luteal.core.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException

class AppointmentAudioChunker {

    class ChunkingException(message: String) : IOException(message)

    fun split(
        source: File,
        outputDir: File,
        chunkDurationSec: Long,
        chunkCount: Int
    ): List<File> {
        if (chunkCount <= 1) return listOf(source)
        outputDir.mkdirs()
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(source.absolutePath)
            val trackIndex = selectAudioTrack(extractor)
                ?: throw ChunkingException("Aucune piste audio")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/mp4"
            val ext = extensionForMime(mime, source)
            val durationUs = format.getLongOrNull(MediaFormat.KEY_DURATION)
            val chunkDurationUs = chunkDurationSec * 1_000_000L
            val results = mutableListOf<File>()
            for (index in 0 until chunkCount) {
                val startUs = index * chunkDurationUs
                val endUs = if (durationUs != null) {
                    minOf((index + 1) * chunkDurationUs, durationUs)
                } else {
                    (index + 1) * chunkDurationUs
                }
                if (durationUs != null && startUs >= durationUs) break
                val outFile = File(outputDir, "chunk_${index.toString().padStart(3, '0')}.$ext")
                extractRange(extractor, trackIndex, format, mime, outFile, startUs, endUs)
                results.add(outFile)
                extractor.unselectTrack(trackIndex)
                extractor.selectTrack(trackIndex)
            }
            if (results.isEmpty()) throw ChunkingException("Découpage impossible")
            return results
        } catch (e: ChunkingException) {
            throw e
        } catch (e: Exception) {
            throw ChunkingException("Découpage impossible pour ce format")
        } finally {
            extractor.release()
        }
    }

    private fun extractRange(
        extractor: MediaExtractor,
        trackIndex: Int,
        format: MediaFormat,
        mime: String,
        outFile: File,
        startUs: Long,
        endUs: Long
    ) {
        if (outFile.exists()) outFile.delete()
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        var muxerTrack = -1
        var started = false
        val buffer = java.nio.ByteBuffer.allocate(256 * 1024)
        val info = android.media.MediaCodec.BufferInfo()
        while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            val sampleTime = extractor.sampleTime
            if (sampleTime > endUs) break
            if (sampleTime < startUs) {
                extractor.advance()
                continue
            }
            if (muxerTrack < 0) {
                muxerTrack = muxer.addTrack(format)
                muxer.start()
                started = true
            }
            info.offset = 0
            info.size = sampleSize
            info.presentationTimeUs = sampleTime - startUs
            info.flags = mapSampleFlags(extractor.sampleFlags)
            muxer.writeSampleData(muxerTrack, buffer, info)
            extractor.advance()
        }
        if (!started) {
            muxer.release()
            outFile.delete()
            throw ChunkingException("Segment audio vide")
        }
        muxer.stop()
        muxer.release()
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private fun extensionForMime(mime: String, source: File): String {
        val fromName = source.extension.lowercase()
        if (fromName.isNotBlank()) return fromName
        return when {
            mime.contains("mpeg") || mime.contains("mp4") -> "m4a"
            mime.contains("webm") -> "webm"
            mime.contains("ogg") -> "ogg"
            else -> "m4a"
        }
    }

    private fun MediaFormat.getLongOrNull(key: String): Long? =
        if (containsKey(key)) getLong(key) else null

    private fun mapSampleFlags(sampleFlags: Int): Int {
        var flags = 0
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        return flags
    }
}
