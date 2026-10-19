package fr.luteal.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "medication_contraindications",
    primaryKeys = ["medicationId", "substanceId"],
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SubstanceEntity::class,
            parentColumns = ["id"],
            childColumns = ["substanceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("substanceId")]
)
data class MedicationContraindicationEntity(
    val medicationId: String,
    val substanceId: String
)
