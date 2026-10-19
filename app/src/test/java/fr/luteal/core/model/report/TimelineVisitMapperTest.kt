package fr.luteal.core.model.report

import fr.luteal.core.model.EventPayload
import fr.luteal.core.model.JournalEvent
import fr.luteal.core.model.JournalEventType
import fr.luteal.core.model.PainMark
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineVisitMapperTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val at = Instant.parse("2026-06-01T10:00:00Z")

    @Test
    fun painCheckChildEnrichesMedicationDetail() {
        val parentId = "intake-1"
        val parent = event(
            id = parentId,
            type = JournalEventType.MEDICATION_INTAKE,
            marks = listOf(PainMark("m1", "pelvis", 3, emptyList()))
        )
        val child = event(
            id = "check-1",
            type = JournalEventType.PAIN_CHECK,
            linkedEventId = parentId,
            marks = listOf(PainMark("m2", "pelvis", 6, emptyList()))
        )
        val visit = TimelineVisitMapper.toVisit(
            event = parent,
            appointment = null,
            professionals = emptyMap(),
            intakeLabel = "Ibuprofène · 1",
            childEvents = listOf(child),
            zone = zone
        )!!
        assertEquals(TimelineVisitKind.MEDICATION_INTAKE, visit.kind)
        assertTrue(visit.detail.contains("Douleur avant : 3"))
        assertTrue(visit.detail.contains("Douleur après : 6"))
        assertNull(TimelineVisitMapper.toVisit(child, null, emptyMap(), null, emptyList(), zone))
    }

    @Test
    fun linkedEventsExcludedFromRoots() {
        val roots = TimelineVisitMapper.rootEvents(
            listOf(
                event("a", JournalEventType.SYMPTOM),
                event("b", JournalEventType.PAIN_CHECK, linkedEventId = "a")
            )
        )
        assertEquals(listOf("a"), roots.map { it.id })
    }

    private fun event(
        id: String,
        type: JournalEventType,
        linkedEventId: String? = null,
        marks: List<PainMark> = emptyList()
    ) = JournalEvent(
        id = id,
        type = type,
        startAt = at,
        endAt = null,
        zoneId = zone.id,
        notes = "",
        linkedEventId = linkedEventId,
        payload = EventPayload.of(type),
        marks = marks,
        createdAt = at,
        updatedAt = at
    )
}
