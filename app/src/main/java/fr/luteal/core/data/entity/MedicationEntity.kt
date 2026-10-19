package fr.luteal.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val form: String,
    val contraindications: String,
    val archived: Boolean,
    val repeatEveryHours: Int? = null
)
