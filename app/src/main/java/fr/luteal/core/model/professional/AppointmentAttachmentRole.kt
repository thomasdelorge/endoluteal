package fr.luteal.core.model.professional

enum class AppointmentAttachmentRole {
    FILE,
    TRANSCRIPT,
    SUMMARY;

    fun toStored(): String = name

    companion object {
        fun fromStored(raw: String): AppointmentAttachmentRole =
            entries.firstOrNull { it.name == raw } ?: FILE
    }
}
