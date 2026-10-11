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
        assertEquals(0L, SafetyBackup.timestamp(folder.root))
    }

    @Test
    fun replacementIsCompleteAndReadingKeepsTheCopy() {
        SafetyBackup.write(folder.root, "previous data")
        SafetyBackup.write(folder.root, "new data 📚")
        assertEquals("new data 📚", SafetyBackup.read(folder.root))
        assertEquals("new data 📚", SafetyBackup.read(folder.root))
        assertTrue(SafetyBackup.exists(folder.root))
        assertTrue(SafetyBackup.timestamp(folder.root) > 0)
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
