package fr.luteal.demo

import fr.luteal.core.model.EndolutealBackup
import fr.luteal.core.model.LutealBackupPayload
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoBackupExportTest {

    @Test
    fun exportDemoBackupForEmulator() {
        val anchor = System.getenv("ANCHOR_DATE")?.let(LocalDate::parse)
            ?: LocalDate.now(ZoneOffset.UTC)
        val payload = DemoBackupFactory.build(anchor)
        assertEquals(EndolutealBackup.FORMAT, payload.backupFormat)
        assertTrue(payload.journalEvents.isNotEmpty())
        assertTrue(payload.dailyRecaps.size >= 31)
        assertTrue(payload.trackEndometriosisEnabled())

        val pretty = Json { prettyPrint = true }
        val text = pretty.encodeToString(LutealBackupPayload.serializer(), payload)
        val dir = demoOutputDir().also { it.mkdirs() }
        dir.resolve("endoluteal-demo-backup.json").writeText(text)
        dir.resolve("luteal-demo-backup.json").writeText(text)
    }

    private fun LutealBackupPayload.trackEndometriosisEnabled(): Boolean =
        preferences.trackEndometriosis

    private fun demoOutputDir(): File =
        File(System.getProperty("demo.backup.dir") ?: "build/demo-data")
}
