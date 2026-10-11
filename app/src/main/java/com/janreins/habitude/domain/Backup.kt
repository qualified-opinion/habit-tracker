package com.janreins.habitude.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.DateTimeException
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
    private const val AUTO_DATE = "([0-9]{4}-[0-9]{2}-[0-9]{2})"
    private const val COPY_SUFFIX = " \\([1-9][0-9]*\\)"
    private const val AUTO_NAME = "$AUTO_PREFIX$AUTO_DATE(?:$COPY_SUFFIX\\.json|\\.json(?:$COPY_SUFFIX)?)"
    private val autoName = Regex("^$AUTO_NAME$")
    private val autoTempName = Regex("^$AUTO_NAME(?:\\.[0-9]+(?:$COPY_SUFFIX)?)?\\.partial(?:$COPY_SUFFIX)?$")

    /** The file name for an automatic backup made on [day]. Sorts by date. */
    fun autoFileName(day: LocalDate): String = "$AUTO_PREFIX$day.json"

    /** Temporary documents cannot be mistaken for finished backups by retention. */
    fun autoTempFileName(name: String, runId: Long? = null): String =
        if (runId == null) "$name.partial" else "$name.$runId.partial"

    /** Reads the date of a finished backup, including a provider's numbered copy suffix. */
    fun autoBackupDate(name: String): LocalDate? = nameDate(autoName, name)

    /** Reads the date of a temporary backup, including unique run names and provider suffixes. */
    fun autoTempDate(name: String): LocalDate? = nameDate(autoTempName, name)

    private fun nameDate(pattern: Regex, name: String): LocalDate? {
        val date = pattern.matchEntire(name)?.groupValues?.get(1) ?: return null
        return try {
            LocalDate.parse(date)
        } catch (e: DateTimeException) {
            null
        }
    }

    fun isAutoTempFileName(name: String): Boolean = autoTempDate(name) != null

    /**
     * Of the file names in the backup folder, the automatic backups to delete so only the
     * newest [keep] dates remain. Copies on a kept date stay until a successful write can
     * remove duplicates. Temporary backups and unrelated files are never touched.
     */
    fun autoBackupsToDelete(names: List<String>, keep: Int = AUTO_KEEP): List<String> {
        require(keep >= 0)
        val byDate = names.mapNotNull { name -> autoBackupDate(name)?.let { it to name } }.groupBy({ it.first }, { it.second })
        return byDate.keys.sortedDescending().drop(keep).flatMap { byDate.getValue(it) }
    }

    /** After saving a complete final copy, removes other final copies for that same date. */
    fun autoBackupDuplicatesToDelete(names: List<String>, savedName: String): List<String> {
        val day = autoBackupDate(savedName) ?: return emptyList()
        return names.filter { it != savedName && autoBackupDate(it) == day }
    }

    /** Only a finished backup for the same date makes it safe to remove a recovery temp. */
    fun autoTempsToDelete(names: List<String>): List<String> {
        val finishedDates = names.mapNotNull(::autoBackupDate).toSet()
        return names.filter { autoTempDate(it)?.let(finishedDates::contains) == true }
    }

    enum class AutoWriteStrategy { RENAME_DONE, FALLBACK_COPY }

    /** A rename finishes the write only when supported, successful, and correctly named. */
    fun autoWriteStrategy(supportsRename: Boolean, renameSucceeded: Boolean, renamedNameAcceptable: Boolean): AutoWriteStrategy =
        if (supportsRename && renameSucceeded && renamedNameAcceptable) AutoWriteStrategy.RENAME_DONE else AutoWriteStrategy.FALLBACK_COPY

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
