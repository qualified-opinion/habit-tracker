package com.janreins.habitude.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class NotesAndAutoBackupTest {
    private val today = LocalDate.of(2026, 10, 8)

    private fun smoking(notes: Map<LocalDate, String>) = HabitWithEntries(
        Habit(1, "Smoking", "🚬", HabitType.BREAK, createdOn = today.minusDays(30)),
        entries = notes.keys,
        notes = notes,
    )

    @Test
    fun notesAreTrimmedAndCapped() {
        assertEquals("Stressful meeting", Notes.clean("  Stressful meeting \n"))
        assertEquals("", Notes.clean("   "))
        assertEquals(Notes.MAX_LENGTH, Notes.clean("a".repeat(800)).length)
    }

    @Test
    fun recentNotesAreNewestFirst() {
        val item = smoking(mapOf(today.minusDays(9) to "Party", today.minusDays(2) to "Long day", today.minusDays(5) to "Bored"))
        assertEquals(listOf("Long day", "Bored", "Party"), Notes.recent(item).map { it.second })
        assertEquals(listOf("Long day"), Notes.recent(item, limit = 1).map { it.second })
    }

    @Test
    fun backupKeepsNotes() {
        val item = smoking(mapOf(today.minusDays(2) to "After dinner with friends"))
        val back = Backup.parse(Backup.export(listOf(item), eveningNudge = false, now = LocalDateTime.of(2026, 10, 8, 9, 0)))
        assertEquals(item, back.habits.single())
    }

    @Test
    fun backupDropsEmptyNotes() {
        val json = """
            {"app":"Habitude","format":1,"exportedAt":"2026-10-08T09:00","habits":[
              {"id":1,"name":"Smoking","emoji":"🚬","type":"BREAK","days":[1,2,3,4,5,6,7],
               "createdOn":"2026-09-01","entries":["2026-10-01"],"notes":{"2026-10-01":"  ","2026-10-02":" Coffee "}}
            ]}
        """.trimIndent()
        assertEquals(mapOf(LocalDate.of(2026, 10, 2) to "Coffee"), Backup.parse(json).habits.single().notes)
    }

    @Test
    fun olderBackupsWithoutNotesStillLoad() {
        val json = """
            {"app":"Habitude","format":1,"exportedAt":"2026-10-08T09:00","habits":[
              {"id":1,"name":"Read","emoji":"📚","type":"BUILD","days":[1,2,3,4,5],"createdOn":"2026-09-01","entries":[]}
            ]}
        """.trimIndent()
        assertTrue(Backup.parse(json).habits.single().notes.isEmpty())
    }

    @Test
    fun autoBackupNamesSortByDate() {
        assertEquals("habitude-auto-2026-10-08.json", Backup.autoFileName(today))
    }

    @Test
    fun temporaryBackupsAreRecognizedAndExcludedFromRetention() {
        val temp = Backup.autoTempFileName(Backup.autoFileName(today))
        assertEquals("habitude-auto-2026-10-08.json.partial", temp)
        assertTrue(Backup.isAutoTempFileName(temp))
        assertTrue(Backup.isAutoTempFileName("habitude-auto-2026-10-08.json (1).partial"))
        assertFalse(Backup.isAutoTempFileName(Backup.autoFileName(today)))
        assertFalse(Backup.isAutoTempFileName("habitude-backup-2026-10-08.json.partial"))
        assertFalse(Backup.isAutoTempFileName("holiday.partial"))
        assertFalse(Backup.isAutoTempFileName("habitude-auto-2026-10-08.json.partial.json"))
        assertTrue(Backup.autoBackupsToDelete(listOf(temp), keep = 0).isEmpty())
    }

    @Test
    fun onlyTheOldestAutoBackupsAreRemoved() {
        val names = listOf(
            "habitude-auto-2026-09-03.json",
            "habitude-auto-2026-10-08.json",
            "habitude-auto-2026-09-10.json",
            "habitude-auto-2026-09-24.json",
            "habitude-auto-2026-09-17.json",
            "habitude-auto-2026-10-01.json",
            // Never touched: a manual export and someone else's file.
            "habitude-backup-2026-01-01.json",
            "holiday.jpg",
        )
        assertEquals(
            listOf("habitude-auto-2026-09-10.json", "habitude-auto-2026-09-03.json"),
            Backup.autoBackupsToDelete(names),
        )
        assertTrue(Backup.autoBackupsToDelete(names.take(3)).isEmpty())
    }
}
