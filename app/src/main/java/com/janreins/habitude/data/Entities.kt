package com.janreins.habitude.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    /** [com.janreins.habitude.domain.HabitType] name. */
    val type: String,
    /** Bit 0 = Monday … bit 6 = Sunday. */
    val scheduleMask: Int,
    /** [java.time.LocalDate.toEpochDay]. */
    val createdOnEpochDay: Long,
    /** Minutes after midnight for the daily reminder; null means no reminder. */
    val reminderMinutes: Int? = null,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    /** Times a week for a habit with a weekly goal; null when it has set days. */
    val weeklyTarget: Int? = null,
    /** How many make a day done for a counted habit; null for a single tick. */
    val dailyTarget: Int? = null,
)

/** One row per habit per day: "done" for build habits, "slipped" for break habits. */
@Entity(
    tableName = "entries",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId")],
)
data class EntryEntity(
    val habitId: Long,
    val epochDay: Long,
)

/** A schedule a build habit used to have, up to and including [untilEpochDay]. */
@Entity(
    tableName = "past_schedules",
    primaryKeys = ["habitId", "untilEpochDay"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId")],
)
data class PastScheduleEntity(
    val habitId: Long,
    val untilEpochDay: Long,
    /** Bit 0 = Monday … bit 6 = Sunday. */
    val scheduleMask: Int,
)

/** A daily goal a counted habit used to have, up to and including [untilEpochDay]. */
@Entity(
    tableName = "past_daily_targets",
    primaryKeys = ["habitId", "untilEpochDay"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId")],
)
data class PastDailyTargetEntity(
    val habitId: Long,
    val untilEpochDay: Long,
    val target: Int,
)

/**
 * A counted habit's running count for one day. The day's [EntryEntity] is kept in step,
 * present once the count reaches the goal, so streaks and charts read only entries.
 */
@Entity(
    tableName = "day_counts",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId")],
)
data class DayCountEntity(
    val habitId: Long,
    val epochDay: Long,
    val amount: Int,
)

/** A short note on one day of a habit, such as what led to a slip. */
@Entity(
    tableName = "notes",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId")],
)
data class NoteEntity(
    val habitId: Long,
    val epochDay: Long,
    val body: String,
)
