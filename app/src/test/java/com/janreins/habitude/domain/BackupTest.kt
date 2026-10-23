package com.janreins.habitude.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class BackupTest {
    private val mon = LocalDate.of(2026, 10, 5)

    private val items = listOf(
        HabitWithEntries(
            Habit(3, "Read \"10\" pages", "📚", HabitType.BUILD, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), mon, reminderMinutes = 8 * 60),
            setOf(mon, mon.plusDays(4)),
        ),
        HabitWithEntries(
            Habit(7, "Doomscrolling", "📱", HabitType.BREAK, DayOfWeek.entries.toSet(), mon.minusDays(30)),
            setOf(mon.minusDays(2)),
        ),
    )

    @Test
    fun roundTripKeepsEverything() {
        val text = Backup.export(items, eveningNudge = false, now = LocalDateTime.of(2026, 10, 8, 9, 30, 15, 123))
        val back = Backup.parse(text)
        assertEquals(items, back.habits)
        assertEquals(false, back.eveningNudge)
        assertEquals("2026-10-08T09:30:15", back.exportedAt)
    }

    @Test
    fun emptyBackupIsFine() {
        val back = Backup.parse(Backup.export(emptyList(), true, LocalDateTime.of(2026, 1, 1, 0, 0)))
        assertEquals(emptyList<HabitWithEntries>(), back.habits)
    }

    @Test
    fun ignoresUnknownFieldsFromNewerMinorChanges() {
        val text = """{"app":"Habitude","format":1,"exportedAt":"x","extra":42,
            "habits":[{"id":1,"name":"Run","emoji":"🏃","type":"BUILD","days":[1,3],"createdOn":"2026-10-05","entries":["2026-10-05"],"colour":"red"}]}"""
        val back = Backup.parse(text)
        assertEquals("Run", back.habits.single().habit.name)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), back.habits.single().habit.schedule)
    }

    @Test
    fun rejectsOtherFiles() {
        val e = assertThrows(BackupException::class.java) { Backup.parse("""{"hello":"world"}""") }
        assertEquals("This doesn't look like a Habitude backup.", e.message)
        assertThrows(BackupException::class.java) { Backup.parse("not json at all") }
        assertThrows(BackupException::class.java) {
            Backup.parse("""{"app":"Other","format":1,"exportedAt":"x","habits":[]}""")
        }
    }

    @Test
    fun rejectsNewerFormatAndBadData() {
        assertThrows(BackupException::class.java) {
            Backup.parse("""{"app":"Habitude","format":2,"exportedAt":"x","habits":[]}""")
        }
        assertThrows(BackupException::class.java) {
            Backup.parse("""{"app":"Habitude","format":1,"exportedAt":"x","habits":[{"id":1,"name":"A","emoji":"x","type":"MAYBE","days":[],"createdOn":"2026-10-05","entries":[]}]}""")
        }
        assertThrows(BackupException::class.java) {
            Backup.parse("""{"app":"Habitude","format":1,"exportedAt":"x","habits":[{"id":1,"name":"A","emoji":"x","type":"BUILD","days":[9],"createdOn":"2026-10-05","entries":[]}]}""")
        }
        assertThrows(BackupException::class.java) {
            Backup.parse("""{"app":"Habitude","format":1,"exportedAt":"x","habits":[{"id":1,"name":"A","emoji":"x","type":"BUILD","days":[1],"createdOn":"yesterday","entries":[]}]}""")
        }
    }

    @Test
    fun keepsTheEveningCheckTime() {
        val text = Backup.export(items, eveningNudge = true, now = LocalDateTime.of(2026, 10, 18, 9, 0), eveningNudgeMinutes = 21 * 60 + 30)
        assertEquals(21 * 60 + 30, Backup.parse(text).eveningNudgeMinutes)
    }

    @Test
    fun olderBackupWithoutTheEveningCheckTimeLeavesItAlone() {
        val back = Backup.parse("""{"app":"Habitude","format":1,"exportedAt":"x","eveningNudge":true,"habits":[]}""")
        assertEquals(true, back.eveningNudge)
        assertNull(back.eveningNudgeMinutes)
    }

    @Test
    fun ignoresAnEveningCheckTimeThatIsNotATimeOfDay() {
        for (bad in listOf(-1, 24 * 60, 99_999)) {
            val back = Backup.parse("""{"app":"Habitude","format":1,"exportedAt":"x","eveningNudgeMinutes":$bad,"habits":[]}""")
            assertNull(back.eveningNudgeMinutes)
        }
    }
}
