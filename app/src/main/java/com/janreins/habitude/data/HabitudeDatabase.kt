package com.janreins.habitude.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HabitEntity::class, EntryEntity::class, PastScheduleEntity::class, DayCountEntity::class, NoteEntity::class],
    version = 7,
    exportSchema = true,
)
abstract class HabitudeDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao

    companion object {
        /** Version 2 adds an optional daily reminder time to each habit. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN reminderMinutes INTEGER")
            }
        }

        /** Version 3 keeps old schedules, so changing a habit's days doesn't rewrite its past. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `past_schedules` (`habitId` INTEGER NOT NULL, " +
                        "`untilEpochDay` INTEGER NOT NULL, `scheduleMask` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`habitId`, `untilEpochDay`), FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_past_schedules_habitId` ON `past_schedules` (`habitId`)")
            }
        }

        /** Version 4 lets a habit be archived. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Version 5 adds "times a week" goals. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN weeklyTarget INTEGER")
            }
        }

        /** Version 6 adds habits counted through the day, like glasses of water. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN dailyTarget INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `day_counts` (`habitId` INTEGER NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, `amount` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`habitId`, `epochDay`), FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_day_counts_habitId` ON `day_counts` (`habitId`)")
            }
        }

        /** Version 7 adds a short note on any day. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes` (`habitId` INTEGER NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, `body` TEXT NOT NULL, " +
                        "PRIMARY KEY(`habitId`, `epochDay`), FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_habitId` ON `notes` (`habitId`)")
            }
        }

        /** All supported upgrades, also used by the migration tests. */
        internal val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

        fun create(context: Context): HabitudeDatabase =
            Room.databaseBuilder(context, HabitudeDatabase::class.java, "habitude.db")
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
