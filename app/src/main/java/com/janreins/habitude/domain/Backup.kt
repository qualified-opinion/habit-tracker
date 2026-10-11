package com.janreins.habitude.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The backup file: plain, readable JSON holding every habit and every day logged against it.
 * Bump [Backup.FORMAT_VERSION] if the shape ever changes, and keep reading older versions.
 */
@Serializable
data class BackupFile(
    val app: String = Backup.APP_NAME,
    @SerialName("format") val formatVersion: Int = Backup.FORMAT_VERSION,
    val exportedAt: String,
    val habits: List<BackupHabit>,
    val eveningNudge: Boolean? = null,
)

@Serializable
data class BackupHabit(
    val id: Long,
    val name: String,
    val emoji: String,
    /** "BUILD" or "BREAK". */
    val type: String,
    /** ISO day numbers, 1 = Monday … 7 = Sunday. */
    val days: List<Int>,
    /** yyyy-MM-dd */
    val createdOn: String,
    val reminderMinutes: Int? = null,
    /** yyyy-MM-dd for every day done (build) or slipped (break). */
    val entries: List<String>,
    /** Earlier schedules, oldest first. Missing in backups from before schedule history. */
    val pastSchedules: List<BackupPastSchedule> = emptyList(),
    val archived: Boolean = false,
    /** Times a week, for a habit with a weekly goal instead of set days. */
    val weeklyTarget: Int? = null,
    /** How many make a day done, for a habit counted through the day. */
    val dailyTarget: Int? = null,
    /** yyyy-MM-dd to the count that day, for a counted habit. */
    val counts: Map<String, Int> = emptyMap(),
    /** yyyy-MM-dd to the note written on that day. */
    val notes: Map<String, String> = emptyMap(),
)

@Serializable
data class BackupPastSchedule(
    /** yyyy-MM-dd, the last day this schedule applied. */
    val until: String,
    /** ISO day numbers, 1 = Monday … 7 = Sunday. */
    val days: List<Int>,
)

data class BackupContents(
    val habits: List<HabitWithEntries>,
    val eveningNudge: Boolean?,
    val exportedAt: String,
)

class BackupException(message: String) : Exception(message)

object Backup {
    const val APP_NAME = "Habitude"
    const val FORMAT_VERSION = 1

    /** How many automatic backups to keep in the chosen folder; older ones are removed. */
    const val AUTO_KEEP = 4

    private const val AUTO_PREFIX = "habitude-auto-"

    /** The file name for an automatic backup made on [day]. Sorts by date. */
    fun autoFileName(day: LocalDate): String = "$AUTO_PREFIX$day.json"

    /** Temporary documents cannot be mistaken for finished backups by retention. */
    fun autoTempFileName(name: String): String = "$name.partial"

    fun isAutoTempFileName(name: String): Boolean =
        name.startsWith(AUTO_PREFIX) && name.endsWith(".partial")

    /**
     * Of the file names in the backup folder, the automatic backups to delete so only the
     * newest [keep] remain. Anything else in the folder is never touched.
     */
    fun autoBackupsToDelete(names: List<String>, keep: Int = AUTO_KEEP): List<String> =
        names.filter { it.startsWith(AUTO_PREFIX) && it.endsWith(".json") }.sortedDescending().drop(keep)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun export(items: List<HabitWithEntries>, eveningNudge: Boolean, now: LocalDateTime): String =
        json.encodeToString(
            BackupFile.serializer(),
            BackupFile(
                exportedAt = now.withNano(0).toString(),
                eveningNudge = eveningNudge,
                habits = items.map { (habit, entries, counts, notes) ->
                    BackupHabit(
                        id = habit.id,
                        name = habit.name,
                        emoji = habit.emoji,
                        type = habit.type.name,
                        days = habit.schedule.map { it.value }.sorted(),
                        createdOn = habit.createdOn.toString(),
                        reminderMinutes = habit.reminderMinutes,
                        entries = entries.sorted().map { it.toString() },
                        pastSchedules = habit.pastSchedules.map { past ->
                            BackupPastSchedule(past.until.toString(), past.days.map { it.value }.sorted())
                        },
                        archived = habit.archived,
                        weeklyTarget = habit.weeklyTarget,
                        dailyTarget = habit.dailyTarget,
                        counts = counts.toSortedMap().mapKeys { it.key.toString() },
                        notes = notes.toSortedMap().mapKeys { it.key.toString() },
                    )
                },
            ),
        )

    /** Reads a backup, or throws [BackupException] with a message fit to show the user. */
    fun parse(text: String): BackupContents {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupException("This doesn't look like a Habitude backup.")
        } catch (e: IllegalArgumentException) {
            throw BackupException("This doesn't look like a Habitude backup.")
        }
        if (file.app != APP_NAME) throw BackupException("This doesn't look like a Habitude backup.")
        if (file.formatVersion > FORMAT_VERSION) {
            throw BackupException("This backup was made by a newer version of Habitude. Update the app, then try again.")
        }
        if (file.habits.map { it.id }.toSet().size != file.habits.size) {
            throw BackupException("This backup is damaged: two habits share the same id.")
        }
        val habits = file.habits.map { h ->
            try {
                val type = HabitType.valueOf(h.type)
                val days = h.days.map { DayOfWeek.of(it) }.toSet()
                HabitWithEntries(
                    habit = Habit(
                        id = h.id,
                        name = h.name.trim().take(40).ifEmpty { "Habit" },
                        emoji = h.emoji,
                        type = type,
                        schedule = days.ifEmpty { DayOfWeek.entries.toSet() },
                        createdOn = LocalDate.parse(h.createdOn),
                        reminderMinutes = h.reminderMinutes?.takeIf { it in 0 until 24 * 60 },
                        pastSchedules = h.pastSchedules
                            .map { past ->
                                PastSchedule(
                                    LocalDate.parse(past.until),
                                    past.days.map { DayOfWeek.of(it) }.toSet().ifEmpty { DayOfWeek.entries.toSet() },
                                )
                            }
                            .distinctBy { it.until }
                            .sortedBy { it.until },
                        archived = h.archived,
                        weeklyTarget = h.weeklyTarget?.takeIf { type == HabitType.BUILD && it in 1..6 },
                        dailyTarget = h.dailyTarget?.takeIf { type == HabitType.BUILD && it in 2..Counts.MAX_TARGET },
                    ),
                    entries = h.entries.map { LocalDate.parse(it) }.toSet(),
                    counts = h.counts
                        .mapKeys { LocalDate.parse(it.key) }
                        .mapValues { it.value.coerceIn(0, Counts.MAX_COUNT) }
                        .filterValues { it > 0 },
                    notes = h.notes
                        .mapKeys { LocalDate.parse(it.key) }
                        .mapValues { Notes.clean(it.value) }
                        .filterValues { it.isNotEmpty() },
                )
            } catch (e: IllegalArgumentException) {
                throw BackupException("This backup is damaged: \"${h.name}\" couldn't be read.")
            } catch (e: java.time.DateTimeException) {
                throw BackupException("This backup is damaged: \"${h.name}\" has a bad date.")
            }
        }
        if (habits.any { it.habit.id <= 0 }) throw BackupException("This backup is damaged: a habit has no id.")
        return BackupContents(habits, file.eveningNudge, file.exportedAt)
    }
}
