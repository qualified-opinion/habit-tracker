package com.janreins.habitude.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class SafetyBackupTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun missingCopyHasNoTimestamp() {
        assertFalse(SafetyBackup.exists(folder.root))
        assertEquals(0L, SafetyBackup.madeAt(folder.root))
    }

    @Test
    fun replacementIsCompleteAndReadingKeepsTheCopy() {
        SafetyBackup.write(folder.root, "previous data")
        SafetyBackup.write(folder.root, "new data 📚")
        assertEquals("new data 📚", SafetyBackup.read(folder.root))
        assertEquals("new data 📚", SafetyBackup.read(folder.root))
        assertTrue(SafetyBackup.exists(folder.root))
        assertTrue(SafetyBackup.madeAt(folder.root) > 0)
        assertEquals(listOf("pre-import.json"), File(folder.root, "backups").list()!!.toList())
    }

    @Test
    fun importTimeSurvivesReplacementAndReads() {
        SafetyBackup.write(folder.root, "previous", now = 1791702000000L)
        val importedAt = 1791727500000L
        SafetyBackup.write(folder.root, "replacement", now = importedAt)
        assertEquals(importedAt, SafetyBackup.madeAt(folder.root))
        assertEquals("replacement", SafetyBackup.read(folder.root))
        assertEquals(importedAt, SafetyBackup.madeAt(folder.root))
    }

    @Test
    fun olderCopyUsesItsExistingModificationTime() {
        val target = File(folder.root, "backups/pre-import.json")
        assertTrue(target.parentFile!!.mkdirs())
        target.writeText("legacy copy")
        val savedAt = 1791702000000L
        assertTrue(target.setLastModified(savedAt))
        assertEquals(savedAt, SafetyBackup.madeAt(folder.root))
    }

    @Test
    fun failedReplacementKeepsThePreviousImportTime() {
        val importedAt = 1791702000000L
        SafetyBackup.write(folder.root, "previous", now = importedAt)
        assertThrows(IllegalArgumentException::class.java) { SafetyBackup.write(folder.root, "replacement", now = -1L) }
        assertEquals("previous", SafetyBackup.read(folder.root))
        assertEquals(importedAt, SafetyBackup.madeAt(folder.root))
        assertEquals(listOf("pre-import.json"), File(folder.root, "backups").list()!!.toList())
    }

    @Test
    fun failedRenameCleansUpTheTempAndKeepsTheTarget() {
        val target = File(folder.root, "backups/pre-import.json")
        assertTrue(target.mkdirs())
        val marker = File(target, "keep")
        marker.writeText("existing data")
        assertThrows(IOException::class.java) { SafetyBackup.write(folder.root, "replacement") }
        assertEquals("existing data", marker.readText())
        assertEquals(listOf("pre-import.json"), target.parentFile!!.list()!!.toList())
    }
}
