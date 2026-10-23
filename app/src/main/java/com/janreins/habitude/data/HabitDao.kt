package com.janreins.habitude.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY id")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM entries")
    fun observeEntries(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM past_schedules ORDER BY untilEpochDay")
    fun observePastSchedules(): Flow<List<PastScheduleEntity>>

    @Query("SELECT * FROM past_schedules WHERE habitId = :habitId ORDER BY untilEpochDay")
    suspend fun getPastSchedules(habitId: Long): List<PastScheduleEntity>

    @Query("DELETE FROM past_schedules WHERE habitId = :habitId")
    suspend fun deletePastSchedules(habitId: Long)

    @Query("DELETE FROM past_schedules")
    suspend fun deleteAllPastSchedules()

    @Insert
    suspend fun insertPastSchedules(schedules: List<PastScheduleEntity>)

    @Query("SELECT * FROM past_daily_targets ORDER BY untilEpochDay")
    fun observePastDailyTargets(): Flow<List<PastDailyTargetEntity>>

    @Query("SELECT * FROM past_daily_targets WHERE habitId = :habitId ORDER BY untilEpochDay")
    suspend fun getPastDailyTargets(habitId: Long): List<PastDailyTargetEntity>

    @Query("DELETE FROM past_daily_targets WHERE habitId = :habitId")
    suspend fun deletePastDailyTargets(habitId: Long)

    @Query("DELETE FROM past_daily_targets")
    suspend fun deleteAllPastDailyTargets()

    @Insert
    suspend fun insertPastDailyTargets(targets: List<PastDailyTargetEntity>)

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabit(id: Long): HabitEntity?

    @Insert
    suspend fun insertHabit(habit: HabitEntity): Long

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabit(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(entry: EntryEntity)

    @Insert
    suspend fun insertHabits(habits: List<HabitEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntries(entries: List<EntryEntity>)

    @Query("DELETE FROM habits")
    suspend fun deleteAllHabits()

    @Query("DELETE FROM entries")
    suspend fun deleteAllEntries()

    @Delete
    suspend fun deleteEntry(entry: EntryEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM entries WHERE habitId = :habitId AND epochDay = :epochDay)")
    suspend fun hasEntry(habitId: Long, epochDay: Long): Boolean

    @Query("SELECT * FROM day_counts")
    fun observeCounts(): Flow<List<DayCountEntity>>

    @Query("SELECT amount FROM day_counts WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun getCount(habitId: Long, epochDay: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCount(entity: DayCountEntity)

    @Query("DELETE FROM day_counts WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun deleteCount(habitId: Long, epochDay: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCounts(counts: List<DayCountEntity>)

    @Query("DELETE FROM day_counts")
    suspend fun deleteAllCounts()

    @Query("SELECT * FROM notes")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun deleteNote(habitId: Long, epochDay: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
}
