package com.janreins.habitude.data

import com.janreins.habitude.domain.Counts
import com.janreins.habitude.domain.Habit
import com.janreins.habitude.domain.HabitOrder
import com.janreins.habitude.domain.HabitType
import com.janreins.habitude.domain.HabitWithEntries
import com.janreins.habitude.domain.Notes
import com.janreins.habitude.domain.PastDailyTarget
import com.janreins.habitude.domain.PastSchedule
import com.janreins.habitude.domain.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import androidx.room.withTransaction
import java.time.LocalDate

/** [orderStore] keeps the order you put your habits in; every list of habits follows it. */
class HabitRepository(
    private val db: HabitudeDatabase,
    private val orderStore: HabitOrderStore = HabitOrderStore.None,
) {
    private val dao = db.habitDao()

    private val unordered: Flow<List<HabitWithEntries>> =
        combine(
            dao.observeHabits(),
            dao.observeEntries(),
            combine(dao.observePastSchedules(), dao.observePastDailyTargets(), ::Pair),
            dao.observeCounts(),
            dao.observeNotes(),
        ) { habits, entries, (past, pastTargets), counts, notes ->
            val byHabit = entries.groupBy({ it.habitId }, { LocalDate.ofEpochDay(it.epochDay) })
            val pastByHabit = past.groupBy { it.habitId }
            val pastTargetsByHabit = pastTargets.groupBy { it.habitId }
            val countsByHabit = counts.groupBy { it.habitId }
            val notesByHabit = notes.groupBy { it.habitId }
            habits.map {
                HabitWithEntries(
                    habit = it.toDomain(pastByHabit[it.id].orEmpty(), pastTargetsByHabit[it.id].orEmpty()),
                    entries = byHabit[it.id].orEmpty().toSet(),
                    counts = countsByHabit[it.id].orEmpty().associate { c -> LocalDate.ofEpochDay(c.epochDay) to c.amount },
                    notes = notesByHabit[it.id].orEmpty().associate { n -> LocalDate.ofEpochDay(n.epochDay) to n.body },
                )
            }
        }

    val habits: Flow<List<HabitWithEntries>> = combine(unordered, orderStore.order, HabitOrder::sort)

    suspend fun getHabit(id: Long): Habit? = dao.getHabit(id)?.toDomain(dao.getPastSchedules(id), dao.getPastDailyTargets(id))

    /** Everything as it stands right now, for background work such as reminders. */
    suspend fun snapshot(): List<HabitWithEntries> = habits.first()

    suspend fun save(habit: Habit): Long = db.withTransaction {
        val id = if (habit.id == 0L) dao.insertHabit(habit.toEntity()) else habit.id.also { dao.updateHabit(habit.toEntity()) }
        dao.deletePastSchedules(id)
        dao.insertPastSchedules(habit.copy(id = id).pastScheduleEntities())
        dao.deletePastDailyTargets(id)
        dao.insertPastDailyTargets(habit.copy(id = id).pastDailyTargetEntities())
        id
    }

    suspend fun delete(id: Long) = dao.deleteHabit(id)

    /**
     * Moves a habit one place up ([delta] -1) or down (+1) among [shownIds], the habits shown
     * together with it, such as the active ones.
     */
    suspend fun move(id: Long, delta: Int, shownIds: List<Long>) {
        val all = snapshot().map { it.habit.id }
        orderStore.setOrder(HabitOrder.move(all, shownIds, id, delta))
    }

    /**
     * Swaps everything for the contents of a backup, all at once or not at all. The habits'
     * order follows the backup's list.
     */
    suspend fun replaceAll(items: List<HabitWithEntries>) {
        db.withTransaction {
            dao.deleteAllCounts()
            dao.deleteAllNotes()
            dao.deleteAllEntries()
            dao.deleteAllPastSchedules()
            dao.deleteAllPastDailyTargets()
            dao.deleteAllHabits()
            dao.insertHabits(items.map { it.habit.toEntity() })
            dao.insertEntries(
                items.flatMap { item -> item.entries.map { EntryEntity(item.habit.id, it.toEpochDay()) } },
            )
            dao.insertPastSchedules(items.flatMap { it.habit.pastScheduleEntities() })
            dao.insertPastDailyTargets(items.flatMap { it.habit.pastDailyTargetEntities() })
            dao.insertCounts(
                items.flatMap { item -> item.counts.map { (day, n) -> DayCountEntity(item.habit.id, day.toEpochDay(), n) } },
            )
            dao.insertNotes(
                items.flatMap { item -> item.notes.map { (day, text) -> NoteEntity(item.habit.id, day.toEpochDay(), text) } },
            )
        }
        orderStore.setOrder(items.map { it.habit.id })
    }

    suspend fun setEntry(habitId: Long, day: LocalDate, present: Boolean) {
        val entry = EntryEntity(habitId, day.toEpochDay())
        if (present) dao.insertEntry(entry) else dao.deleteEntry(entry)
    }

    /**
     * Adds [delta] (negative to take some off) to a counted habit's count for [day], and marks
     * the day done or not done to match. Read and written in one go, so quick taps all count.
     */
    suspend fun addCount(habitId: Long, day: LocalDate, delta: Int, today: LocalDate = LocalDate.now()) {
        db.withTransaction {
            val target = targetOn(habitId, day) ?: return@withTransaction
            val epochDay = day.toEpochDay()
            val wasDone = dao.hasEntry(habitId, epochDay)
            val count = Counts.next(dao.getCount(habitId, epochDay), wasDone, target, delta)
            writeCount(habitId, epochDay, count, target, wasDone, day.isBefore(today))
        }
    }

    /**
     * After a counted habit's goal changes, marks [day] done or not done by the new goal.
     * Earlier days keep what they had, the same way a schedule change leaves the past alone.
     */
    suspend fun recheckCount(habitId: Long, day: LocalDate) {
        db.withTransaction {
            val target = targetOn(habitId, day) ?: return@withTransaction
            val count = dao.getCount(habitId, day.toEpochDay()) ?: return@withTransaction
            writeCount(habitId, day.toEpochDay(), count, target)
        }
    }

    /** Writes the note for [day], or removes it when [text] is blank. */
    suspend fun setNote(habitId: Long, day: LocalDate, text: String) {
        val note = Notes.clean(text)
        if (note.isEmpty()) dao.deleteNote(habitId, day.toEpochDay()) else dao.upsertNote(NoteEntity(habitId, day.toEpochDay(), note))
    }

    /** The goal [day] is judged by, or null if the habit isn't counted. */
    private suspend fun targetOn(habitId: Long, day: LocalDate): Int? {
        val habit = dao.getHabit(habitId) ?: return null
        if (habit.dailyTarget == null) return null
        return habit.toDomain(emptyList(), dao.getPastDailyTargets(habitId)).dailyTargetOn(day)
    }

    private suspend fun writeCount(habitId: Long, epochDay: Long, count: Int, target: Int, wasDone: Boolean = false, isPastDay: Boolean = false) {
        if (count > 0) dao.upsertCount(DayCountEntity(habitId, epochDay, count)) else dao.deleteCount(habitId, epochDay)
        val entry = EntryEntity(habitId, epochDay)
        if (Counts.doneAfterEdit(wasDone, count, target, isPastDay)) dao.insertEntry(entry) else dao.deleteEntry(entry)
    }
}

private fun HabitEntity.toDomain(past: List<PastScheduleEntity>, pastTargets: List<PastDailyTargetEntity>) = Habit(
    id = id,
    name = name,
    emoji = emoji,
    type = HabitType.valueOf(type),
    schedule = Schedule.fromMask(scheduleMask),
    createdOn = LocalDate.ofEpochDay(createdOnEpochDay),
    reminderMinutes = reminderMinutes,
    archived = archived,
    weeklyTarget = weeklyTarget,
    dailyTarget = dailyTarget,
    pastSchedules = past.sortedBy { it.untilEpochDay }.map {
        PastSchedule(LocalDate.ofEpochDay(it.untilEpochDay), Schedule.fromMask(it.scheduleMask))
    },
    pastDailyTargets = pastTargets.sortedBy { it.untilEpochDay }.map {
        PastDailyTarget(LocalDate.ofEpochDay(it.untilEpochDay), it.target)
    },
)

private fun Habit.pastScheduleEntities() = pastSchedules.map {
    PastScheduleEntity(id, it.until.toEpochDay(), Schedule.toMask(it.days))
}

private fun Habit.pastDailyTargetEntities() = pastDailyTargets.map {
    PastDailyTargetEntity(id, it.until.toEpochDay(), it.target)
}

private fun Habit.toEntity() = HabitEntity(
    id = id,
    name = name,
    emoji = emoji,
    type = type.name,
    scheduleMask = Schedule.toMask(schedule),
    createdOnEpochDay = createdOn.toEpochDay(),
    reminderMinutes = reminderMinutes,
    archived = archived,
    weeklyTarget = weeklyTarget,
    dailyTarget = dailyTarget,
)
