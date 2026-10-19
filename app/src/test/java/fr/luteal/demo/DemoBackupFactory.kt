package fr.luteal.demo

import fr.luteal.core.model.AppointmentBackupDto
import fr.luteal.core.model.BiomarkerBackupDto
import fr.luteal.core.model.CompositionBackupDto
import fr.luteal.core.model.CycleBackupDto
import fr.luteal.core.model.DailyEntryBackupDto
import fr.luteal.core.model.DailyRecapBackupDto
import fr.luteal.core.model.EndolutealBackup
import fr.luteal.core.model.EventPayload
import fr.luteal.core.model.EventPayloadCodec
import fr.luteal.core.model.JournalEventBackupDto
import fr.luteal.core.model.LutealBackupPayload
import fr.luteal.core.model.MedicationBackupDto
import fr.luteal.core.model.MedicationIntakeBackupDto
import fr.luteal.core.model.PainMarkBackupDto
import fr.luteal.core.model.PeriodDayBackupDto
import fr.luteal.core.model.ProfessionalBackupDto
import fr.luteal.core.model.SubstanceBackupDto
import fr.luteal.core.model.SymptomLogBackupDto
import fr.luteal.core.model.TreatmentPlanBackupDto
import fr.luteal.core.model.UserPreferencesBackupDto
import fr.luteal.core.model.recap.RecapBowelPayload
import fr.luteal.core.model.recap.RecapCyclePayload
import fr.luteal.core.model.recap.RecapGeneralPayload
import fr.luteal.core.model.recap.RecapPainMark
import fr.luteal.core.model.recap.RecapPainPayload
import fr.luteal.core.model.recap.RecapPsyPayload
import fr.luteal.core.model.recap.RecapStep
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
object DemoBackupFactory {
    private val json = Json { encodeDefaults = true }

    /** Rolling window of demo recaps / journal rows (≥ 1 calendar month). */
    private const val DEMO_ROLLING_DAYS = 35

    fun build(anchor: LocalDate): LutealBackupPayload {
        fun d(offsetDays: Long) = anchor.plusDays(offsetDays)
        fun instant(day: LocalDate, hour: Int = 12) =
            day.atTime(hour, 0).toInstant(ZoneOffset.UTC).toString()
        val exportedAt = instant(anchor)

        val cycle1Start = d(-58)
        val cycle1End = d(-31)
        val cycle2Start = d(-30)
        val cycle2End = d(-6)
        val cycle3Start = d(-5)

        val proId = "demo-pro-gyn"
        val medId = "demo-med-ibu"
        val subId = "demo-sub-ibu"
        val evtApptPast = "demo-evt-appt-past"
        val evtApptRecent = "demo-evt-appt-recent"

        val apptPastDay = d(-45)
        val apptRecentDay = d(-7)

        fun payload(element: EventPayload): JsonElement =
            json.parseToJsonElement(EventPayloadCodec.encode(element))

        val allSteps = RecapStep.entries.map { it.name }
        val dailyRecaps = buildRollingRecaps(anchor, exportedAt, allSteps)
        val dailyEntries = buildRollingDailyEntries(anchor, exportedAt)
        val biomarkerObservations = buildRollingBiomarkers(anchor, exportedAt)
        val journalBundle = buildRollingJournal(anchor, exportedAt, medId, ::payload, ::instant)

        return LutealBackupPayload(
            backupFormat = EndolutealBackup.FORMAT,
            schemaVersion = 1,
            exportedAt = exportedAt,
            appVersion = "demo-seed",
            cycles = listOf(
                cycle(
                    id = "demo-cycle-1",
                    start = cycle1Start,
                    end = cycle1End,
                    period = listOf(
                        period(cycle1Start, "HEAVY", "First recorded day", listOf("cramps")),
                        period(d(-57), "MEDIUM"),
                        period(d(-56), "LIGHT"),
                        period(d(-55), "SPOTTING")
                    )
                ),
                cycle(
                    id = "demo-cycle-2",
                    start = cycle2Start,
                    end = cycle2End,
                    period = listOf(
                        period(cycle2Start, "HEAVY", "Cycle start", listOf("cramps")),
                        period(d(-29), "HEAVY"),
                        period(d(-28), "MEDIUM"),
                        period(d(-27), "LIGHT"),
                        period(d(-26), "SPOTTING")
                    )
                ),
                cycle(
                    id = "demo-cycle-3",
                    start = cycle3Start,
                    end = null,
                    period = listOf(
                        period(cycle3Start, "HEAVY", "Current cycle", listOf("cramps")),
                        period(d(-4), "MEDIUM"),
                        period(d(-3), "LIGHT"),
                        period(d(-2), "SPOTTING")
                    )
                )
            ),
            dailyEntries = dailyEntries,
            symptomLogs = buildRollingSymptomLogs(anchor, exportedAt),
            biomarkerObservations = biomarkerObservations,
            journalEvents = journalBundle.events + listOf(
                journal(
                    evtApptPast,
                    "APPOINTMENT",
                    instant(apptPastDay, 10),
                    "Consultation de suivi endométriose",
                    payload(EventPayload.Appointment),
                    exportedAt
                ),
                journal(
                    evtApptRecent,
                    "APPOINTMENT",
                    instant(apptRecentDay, 14),
                    "Échographie pelvienne",
                    payload(EventPayload.Appointment),
                    exportedAt
                )
            ),
            painMarks = journalBundle.painMarks,
            substances = listOf(
                SubstanceBackupDto(
                    id = subId,
                    name = "Ibuprofène",
                    drugClass = "NSAID",
                    maxDailyMg = 1200.0,
                    maxPerIntakeMg = 400.0,
                    minIntervalMinutes = 360,
                    validated = true
                )
            ),
            medications = listOf(
                MedicationBackupDto(
                    id = medId,
                    name = "Ibuprofène 400 mg",
                    form = "comprimé",
                    compositions = listOf(CompositionBackupDto(subId, 400.0))
                )
            ),
            medicationIntakes = journalBundle.medicationIntakes,
            treatmentPlans = listOf(
                TreatmentPlanBackupDto(
                    id = "demo-plan-ibu",
                    medicationId = medId,
                    unitsPerDose = 1.0,
                    timesOfDay = listOf("08:00", "20:00"),
                    startDate = cycle3Start.toString(),
                    active = true
                )
            ),
            professionals = listOf(
                ProfessionalBackupDto(
                    id = proId,
                    name = "Dr Martin",
                    specialty = "GYNECOLOGIST",
                    place = "CHU démo",
                    phone = "0102030405"
                )
            ),
            appointments = listOf(
                AppointmentBackupDto(
                    eventId = evtApptPast,
                    professionalId = proId,
                    location = "Consultation externe",
                    report = "Suivi endométriose, adaptation traitement",
                    reportStatus = "DONE"
                ),
                AppointmentBackupDto(
                    eventId = evtApptRecent,
                    professionalId = proId,
                    location = "Imagerie",
                    description = "Échographie pelvienne",
                    reportStatus = "PENDING"
                )
            ),
            dailyRecaps = dailyRecaps,
            preferences = UserPreferencesBackupDto(
                userRole = "PRIMARY_TRACKER",
                locale = "fr",
                trackPmdd = true,
                trackPms = true,
                trackEndometriosis = true,
                ageBand = "age_30_34",
                temperatureUnit = "CELSIUS"
            )
        )
    }

    private fun dailyRecap(
        date: LocalDate,
        exportedAt: String,
        cycle: RecapCyclePayload,
        pain: RecapPainPayload,
        psy: RecapPsyPayload,
        general: RecapGeneralPayload,
        bowel: RecapBowelPayload,
        completedSteps: List<String>
    ): DailyRecapBackupDto = DailyRecapBackupDto(
        date = date.toString(),
        cycle = json.encodeToString(cycle),
        pain = json.encodeToString(pain),
        psy = json.encodeToString(psy),
        general = json.encodeToString(general),
        bowel = json.encodeToString(bowel),
        completedSteps = completedSteps,
        updatedAt = exportedAt
    )

    private fun cycle(
        id: String,
        start: LocalDate,
        end: LocalDate?,
        period: List<PeriodDayBackupDto>
    ) = CycleBackupDto(
        id = id,
        startDate = start.toString(),
        endDate = end?.toString(),
        periodDays = period
    )

    private fun period(
        date: LocalDate,
        intensity: String,
        notes: String = "",
        symptoms: List<String> = emptyList()
    ) = PeriodDayBackupDto(date.toString(), intensity, notes, symptoms)

    private fun entry(
        date: LocalDate,
        updatedAt: String,
        bleeding: String?,
        pain: Int,
        mood: Int,
        energy: Int,
        symptoms: List<String>,
        notes: String
    ) = DailyEntryBackupDto(
        date = date.toString(),
        bleedingIntensity = bleeding,
        painLevel = pain,
        moodLevel = mood,
        energyLevel = energy,
        symptomIds = symptoms,
        notes = notes,
        updatedAt = updatedAt
    )

    private fun symptom(
        id: String,
        timestamp: String,
        date: LocalDate,
        symptomId: String,
        severity: Int,
        notes: String
    ) = SymptomLogBackupDto(id, timestamp, date.toString(), symptomId, severity, notes)

    private fun journal(
        id: String,
        type: String,
        startAt: String,
        notes: String,
        payload: JsonElement,
        exportedAt: String,
        linkedEventId: String? = null
    ) = JournalEventBackupDto(
        id = id,
        type = type,
        startAt = startAt,
        endAt = null,
        zoneId = "Europe/Paris",
        notes = notes,
        linkedEventId = linkedEventId,
        payload = payload,
        createdAt = exportedAt,
        updatedAt = exportedAt
    )

    private fun rollingDayOffsets(): List<Long> =
        (DEMO_ROLLING_DAYS - 1 downTo 0).map { (it - (DEMO_ROLLING_DAYS - 1)).toLong() }

    private fun isDemoPeriodDay(daysAgo: Int): Boolean =
        daysAgo in 2..5 || daysAgo in 26..30

    private fun buildRollingRecaps(
        anchor: LocalDate,
        exportedAt: String,
        allSteps: List<String>
    ): List<DailyRecapBackupDto> {
        val partialSteps = listOf(
            RecapStep.CYCLE.name,
            RecapStep.PAIN.name,
            RecapStep.GENERAL.name
        )
        return rollingDayOffsets().map { offset ->
            val day = anchor.plusDays(offset)
            val daysAgo = -offset.toInt()
            val period = isDemoPeriodDay(daysAgo)
            val wave = daysAgo % 7
            val dys = (2 + wave + if (period) 4 else 0).coerceIn(1, 9)
            val marks = if (daysAgo % 3 == 0) {
                listOf(
                    RecapPainMark(
                        id = "recap-mark-$daysAgo",
                        zone = if (daysAgo % 2 == 0) "f08" else "b02",
                        intensity = dys,
                        painTypes = listOf("cramp"),
                        pinSide = if (daysAgo % 2 == 0) "front" else "back",
                        pinX = 0.4f,
                        pinY = 0.55f
                    )
                )
            } else {
                emptyList()
            }
            dailyRecap(
                date = day,
                exportedAt = exportedAt,
                cycle = RecapCyclePayload(
                    acceptEstimatedPhase = true,
                    hasBleeding = period,
                    bleedingIntensity = when {
                        !period -> ""
                        daysAgo in 26..28 -> "HEAVY"
                        daysAgo == 29 -> "MEDIUM"
                        else -> "LIGHT"
                    }
                ),
                pain = RecapPainPayload(
                    marks = marks,
                    dysmenorrhea = dys,
                    dyspareunia = (dys / 2).coerceAtMost(6),
                    dyschezia = (1 + wave / 2 + if (period) 2 else 0),
                    dysuria = (wave % 3),
                    chronicPelvicPain = (2 + (daysAgo % 4)),
                    notes = if (dys >= 6) "Journée difficile" else ""
                ),
                psy = RecapPsyPayload(
                    mood = (5 - dys / 2).coerceIn(1, 5),
                    anxiety = (2 + wave / 2).coerceIn(1, 5),
                    irritability = (1 + dys / 3).coerceIn(1, 5)
                ),
                general = RecapGeneralPayload(
                    fatigue = (2 + dys / 2).coerceIn(1, 5),
                    stress = (2 + wave / 3).coerceIn(1, 5),
                    sleepQuality = (4 - dys / 3).coerceIn(1, 5),
                    headache = if (daysAgo % 5 == 0) 3 else 1,
                    alcoholDrinks = if (daysAgo % 11 == 0) 1 else 0,
                    dailyImpact = when {
                        dys >= 7 -> "BEDREST"
                        dys >= 5 -> "REDUCED_ACTIVITY"
                        else -> "NONE"
                    }
                ),
                bowel = RecapBowelPayload(
                    bloating = (2 + wave / 2 + if (period) 1 else 0).coerceIn(1, 5),
                    bristolType = 3 + (daysAgo % 3)
                ),
                completedSteps = if (daysAgo % 9 == 0) partialSteps else allSteps
            )
        }
    }

    private fun buildRollingDailyEntries(
        anchor: LocalDate,
        exportedAt: String
    ): List<DailyEntryBackupDto> =
        rollingDayOffsets().map { offset ->
            val day = anchor.plusDays(offset)
            val daysAgo = -offset.toInt()
            val period = isDemoPeriodDay(daysAgo)
            val pain = (2 + daysAgo % 5 + if (period) 2 else 0).coerceIn(1, 5)
            entry(
                date = day,
                updatedAt = exportedAt,
                bleeding = when {
                    !period -> null
                    daysAgo in 26..28 -> "HEAVY"
                    daysAgo == 29 -> "MEDIUM"
                    daysAgo == 30 -> "LIGHT"
                    daysAgo in 2..3 -> "MEDIUM"
                    else -> "SPOTTING"
                },
                pain = pain,
                mood = (5 - pain / 2).coerceIn(1, 5),
                energy = (4 - pain / 2 + (daysAgo % 3)).coerceIn(1, 5),
                symptoms = buildList {
                    if (period) add("cramps")
                    if (daysAgo % 5 == 0) add("headache")
                    if (daysAgo % 8 == 0) add("fatigue")
                },
                notes = if (period) "Règles" else ""
            )
        }

    private fun buildRollingBiomarkers(anchor: LocalDate, exportedAt: String): List<BiomarkerBackupDto> =
        rollingDayOffsets().mapIndexed { index, offset ->
            val day = anchor.plusDays(offset)
            val daysAgo = -offset.toInt()
            val follicular = daysAgo in 15..22
            BiomarkerBackupDto(
                date = day.toString(),
                bbtCelsius = 36.32 + (index % 6) * 0.04 + if (follicular) 0.0 else 0.28,
                bbtTime = "07:10",
                cervicalSensation = if (follicular) "WET" else "DRY",
                cervicalTexture = if (follicular) "EGG_WHITE" else "STICKY",
                lhTestResult = if (daysAgo == 18) "PEAK_POSITIVE" else "LOW",
                updatedAt = exportedAt
            )
        }

    private fun buildRollingSymptomLogs(anchor: LocalDate, exportedAt: String): List<SymptomLogBackupDto> =
        rollingDayOffsets()
            .filter { (-it).toInt() % 4 == 0 }
            .map { offset ->
                val day = anchor.plusDays(offset)
                val daysAgo = -offset.toInt()
                val id = "demo-symptom-log-$daysAgo"
                val symptomId = when {
                    isDemoPeriodDay(daysAgo) -> "cramps"
                    daysAgo % 8 == 0 -> "fatigue"
                    else -> "headache"
                }
                symptom(
                    id = id,
                    timestamp = exportedAt,
                    date = day,
                    symptomId = symptomId,
                    severity = (2 + daysAgo % 4).coerceIn(1, 5),
                    notes = "Journal démo"
                )
            }

    private data class JournalBundle(
        val events: List<JournalEventBackupDto>,
        val painMarks: List<PainMarkBackupDto>,
        val medicationIntakes: List<MedicationIntakeBackupDto>
    )

    private fun buildRollingJournal(
        anchor: LocalDate,
        exportedAt: String,
        medId: String,
        payload: (EventPayload) -> JsonElement,
        instant: (LocalDate, Int) -> String
    ): JournalBundle {
        val events = mutableListOf<JournalEventBackupDto>()
        val marks = mutableListOf<PainMarkBackupDto>()
        val intakes = mutableListOf<MedicationIntakeBackupDto>()
        rollingDayOffsets().forEach { offset ->
            val day = anchor.plusDays(offset)
            val daysAgo = -offset.toInt()
            if (daysAgo % 3 != 0) return@forEach
            val symptomId = "demo-evt-symptom-$daysAgo"
            val pain = (3 + daysAgo % 5 + if (isDemoPeriodDay(daysAgo)) 2 else 0).coerceIn(1, 9)
            events += journal(
                symptomId,
                "SYMPTOM",
                instant(day, 8),
                if (pain >= 6) "Douleurs marquées" else "Douleur légère",
                payload(EventPayload.Symptom),
                exportedAt
            )
            marks += PainMarkBackupDto(
                id = "demo-pain-mark-$daysAgo",
                eventId = symptomId,
                zone = if (daysAgo % 2 == 0) "f08" else "b02",
                intensity = pain,
                painTypes = listOf(if (pain >= 6) "stabbing" else "dull"),
                pinSide = if (daysAgo % 2 == 0) "front" else "back",
                pinX = 0.42f,
                pinY = 0.56f
            )
            if (daysAgo % 2 == 0) {
                val checkId = "demo-evt-pain-check-$daysAgo"
                events += journal(
                    checkId,
                    "PAIN_CHECK",
                    instant(day, 8),
                    "",
                    payload(EventPayload.PainCheck),
                    exportedAt,
                    linkedEventId = symptomId
                )
            }
            if (pain >= 5 && daysAgo % 2 == 1) {
                val medEventId = "demo-evt-med-$daysAgo"
                events += journal(
                    medEventId,
                    "MEDICATION_INTAKE",
                    instant(day, 9),
                    "",
                    payload(EventPayload.MedicationIntake(dangerAcknowledged = true)),
                    exportedAt
                )
                intakes += MedicationIntakeBackupDto(medEventId, medId, 1.0)
            }
        }
        return JournalBundle(events, marks, intakes)
    }
}
