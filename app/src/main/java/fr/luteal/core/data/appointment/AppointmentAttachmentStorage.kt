package fr.luteal.core.data.appointment

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppointmentAttachmentStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val root: File
        get() = File(context.filesDir, ROOT_DIR).also { it.mkdirs() }

    fun fileFor(relativePath: String): File = File(context.filesDir, relativePath)

    fun eventDir(eventId: String): File =
        File(root, eventId).also { it.mkdirs() }

    fun relativePathFor(eventId: String, fileName: String): String =
        "$ROOT_DIR/$eventId/$fileName"

    fun deleteEventFiles(eventId: String) {
        File(root, eventId).deleteRecursively()
    }

    fun deleteFile(relativePath: String) {
        fileFor(relativePath).delete()
    }

    fun deleteAll() {
        root.deleteRecursively()
    }

    fun copyFromStream(
        eventId: String,
        displayName: String,
        mimeType: String,
        input: InputStream
    ): StoredFile {
        val safeName = sanitizeFileName(displayName)
        val dest = uniqueFile(eventDir(eventId), safeName)
        input.use { ins ->
            dest.outputStream().use { out -> ins.copyTo(out) }
        }
        val relative = relativePathFor(eventId, dest.name)
        return StoredFile(relativePath = relative, sizeBytes = dest.length(), mimeType = mimeType)
    }

    fun writeText(
        eventId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): StoredFile {
        val dest = uniqueFile(eventDir(eventId), sanitizeFileName(fileName))
        dest.writeBytes(bytes)
        val relative = relativePathFor(eventId, dest.name)
        return StoredFile(relativePath = relative, sizeBytes = dest.length(), mimeType = mimeType)
    }

    private fun uniqueFile(dir: File, baseName: String): File {
        var candidate = File(dir, baseName)
        if (!candidate.exists()) return candidate
        val dot = baseName.lastIndexOf('.')
        val stem = if (dot > 0) baseName.substring(0, dot) else baseName
        val ext = if (dot > 0) baseName.substring(dot) else ""
        var n = 2
        while (candidate.exists()) {
            candidate = File(dir, "$stem-$n$ext")
            n++
        }
        return candidate
    }

    private fun sanitizeFileName(raw: String): String {
        val trimmed = raw.trim().ifBlank { "piece_jointe" }
        return trimmed.replace(Regex("""[\\/:*?"<>|]"""), "_").take(180)
    }

    data class StoredFile(
        val relativePath: String,
        val sizeBytes: Long,
        val mimeType: String
    )

    companion object {
        const val ROOT_DIR = "appointment-attachments"
    }
}
