package fr.luteal.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pain_marks ADD COLUMN pinSide TEXT")
        db.execSQL("ALTER TABLE pain_marks ADD COLUMN pinX REAL")
        db.execSQL("ALTER TABLE pain_marks ADD COLUMN pinY REAL")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Rebuild so reimbursementStatus is NOT NULL without a SQL default.
        // ALTER TABLE ... DEFAULT '' does not match the Room entity and
        // fails open on upgrade from schema 11.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `appointments_new` (
                `eventId` TEXT NOT NULL,
                `professionalId` TEXT NOT NULL,
                `calendarEventKey` TEXT,
                `location` TEXT NOT NULL,
                `report` TEXT NOT NULL,
                `reportStatus` TEXT NOT NULL,
                `feeCents` INTEGER,
                `reimbursementStatus` TEXT NOT NULL,
                `cpamCents` INTEGER,
                `mutuelleCents` INTEGER,
                PRIMARY KEY(`eventId`),
                FOREIGN KEY(`eventId`) REFERENCES `journal_events`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO appointments_new (
                eventId, professionalId, calendarEventKey, location, report, reportStatus,
                feeCents, reimbursementStatus, cpamCents, mutuelleCents
            )
            SELECT eventId, professionalId, calendarEventKey, location, report, reportStatus,
                NULL, '', NULL, NULL
            FROM appointments
            """.trimIndent()
        )
        db.execSQL("DROP TABLE appointments")
        db.execSQL("ALTER TABLE appointments_new RENAME TO appointments")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_appointments_professionalId` ON `appointments` (`professionalId`)"
        )
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE appointments ADD COLUMN description TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE appointments ADD COLUMN titleEdited INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE appointments ADD COLUMN locationEdited INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `medication_contraindications` (
                `medicationId` TEXT NOT NULL,
                `substanceId` TEXT NOT NULL,
                PRIMARY KEY(`medicationId`, `substanceId`),
                FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`substanceId`) REFERENCES `substances`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_medication_contraindications_substanceId` " +
                "ON `medication_contraindications` (`substanceId`)"
        )
    }
}
