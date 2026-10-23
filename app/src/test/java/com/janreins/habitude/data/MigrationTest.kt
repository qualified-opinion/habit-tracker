package com.janreins.habitude.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HabitudeDatabase::class.java)

    @Test
    fun migrateFromVersion1() = migrateFrom(1)
    @Test
    fun migrateFromVersion2() = migrateFrom(2)
    @Test
    fun migrateFromVersion3() = migrateFrom(3)
    @Test
    fun migrateFromVersion4() = migrateFrom(4)
    @Test
    fun migrateFromVersion5() = migrateFrom(5)
    @Test
    fun migrateFromVersion6() = migrateFrom(6)

    @Test
    fun migrateFromVersion7() = migrateFrom(7)

    /** The latest version is also exercised so old daily goals have a populated fixture. */
    @Test
    fun validateVersion8() = migrateFrom(8)

    private fun migrateFrom(version: Int) {
        val name = "migration-$version.db"
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(name)
        try {
            helper.createDatabase(name, version).use { insertRows(it, version) }
            helper.runMigrationsAndValidate(name, 8, true, *HabitudeDatabase.ALL_MIGRATIONS).use { db ->
                assertRows(db, version)
                db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            }
            val room = Room.databaseBuilder(context, HabitudeDatabase::class.java, name)
                .addMigrations(*HabitudeDatabase.ALL_MIGRATIONS)
                .build()
            try {
                val habit = runBlocking { room.habitDao().getHabit(1) }
                assertNotNull(habit)
                assertEquals("Read", habit!!.name)
                assertEquals(if (version >= 2) 480 else null, habit.reminderMinutes)
                assertEquals(version >= 4, habit.archived)
                assertEquals(if (version >= 5) 3 else null, habit.weeklyTarget)
                assertEquals(if (version >= 6) 5 else null, habit.dailyTarget)
                val pastTargets = runBlocking { room.habitDao().getPastDailyTargets(1) }
                assertEquals(if (version >= 8) listOf(PastDailyTargetEntity(1, 20001, 4)) else emptyList<PastDailyTargetEntity>(), pastTargets)
            } finally {
                room.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun insertRows(db: SupportSQLiteDatabase, version: Int) {
        val columns = mutableListOf("id", "name", "emoji", "type", "scheduleMask", "createdOnEpochDay")
        val values = mutableListOf<Any>(1L, "Read", "📚", "BUILD", 31, 20000L)
        if (version >= 2) {
            columns += "reminderMinutes"
            values += 480
        }
        if (version >= 4) {
            columns += "archived"
            values += 1
        }
        if (version >= 5) {
            columns += "weeklyTarget"
            values += 3
        }
        if (version >= 6) {
            columns += "dailyTarget"
            values += 5
        }
        db.execSQL(
            "INSERT INTO habits (${columns.joinToString()}) VALUES (${columns.joinToString { "?" }})",
            values.toTypedArray(),
        )
        db.execSQL("INSERT INTO entries (habitId, epochDay) VALUES (1, 20001)")
        if (version >= 3) db.execSQL("INSERT INTO past_schedules (habitId, untilEpochDay, scheduleMask) VALUES (1, 20001, 127)")
        if (version >= 6) db.execSQL("INSERT INTO day_counts (habitId, epochDay, amount) VALUES (1, 20001, 5)")
        if (version >= 7) db.execSQL("INSERT INTO notes (habitId, epochDay, body) VALUES (1, 20001, 'Before bed')")
        if (version >= 8) db.execSQL("INSERT INTO past_daily_targets (habitId, untilEpochDay, target) VALUES (1, 20001, 4)")
    }

    private fun assertRows(db: SupportSQLiteDatabase, version: Int) {
        db.query("SELECT * FROM habits").use { row ->
            assertTrue(row.moveToFirst())
            assertEquals(1L, row.getLong(row.getColumnIndexOrThrow("id")))
            assertEquals("Read", row.getString(row.getColumnIndexOrThrow("name")))
            assertEquals("📚", row.getString(row.getColumnIndexOrThrow("emoji")))
            assertEquals("BUILD", row.getString(row.getColumnIndexOrThrow("type")))
            assertEquals(31, row.getInt(row.getColumnIndexOrThrow("scheduleMask")))
            assertEquals(20000L, row.getLong(row.getColumnIndexOrThrow("createdOnEpochDay")))
            assertEquals(if (version >= 4) 1 else 0, row.getInt(row.getColumnIndexOrThrow("archived")))
            for ((column, since, value) in listOf(Triple("reminderMinutes", 2, 480), Triple("weeklyTarget", 5, 3), Triple("dailyTarget", 6, 5))) {
                val index = row.getColumnIndexOrThrow(column)
                if (version >= since) assertEquals(value, row.getInt(index)) else assertTrue(row.isNull(index))
            }
            assertFalse(row.moveToNext())
        }
        db.query("SELECT habitId, epochDay FROM entries").use {
            assertTrue(it.moveToFirst())
            assertEquals(1L, it.getLong(0))
            assertEquals(20001L, it.getLong(1))
            assertFalse(it.moveToNext())
        }
        assertHistory(db, "past_schedules", "untilEpochDay", "scheduleMask", if (version >= 3) 127 else null)
        assertHistory(db, "day_counts", "epochDay", "amount", if (version >= 6) 5 else null)
        assertHistory(db, "past_daily_targets", "untilEpochDay", "target", if (version >= 8) 4 else null)
        db.query("SELECT habitId, epochDay, body FROM notes").use {
            assertEquals(version >= 7, it.moveToFirst())
            if (version >= 7) {
                assertEquals(1L, it.getLong(0))
                assertEquals(20001L, it.getLong(1))
                assertEquals("Before bed", it.getString(2))
                assertFalse(it.moveToNext())
            }
        }
    }

    private fun assertHistory(db: SupportSQLiteDatabase, table: String, day: String, column: String, value: Int?) {
        db.query("SELECT habitId, $day, $column FROM $table").use {
            assertEquals(value != null, it.moveToFirst())
            if (value != null) {
                assertEquals(1L, it.getLong(0))
                assertEquals(20001L, it.getLong(1))
                assertEquals(value, it.getInt(2))
                assertFalse(it.moveToNext())
            }
        }
    }
}
