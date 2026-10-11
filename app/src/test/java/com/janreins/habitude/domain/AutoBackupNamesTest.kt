package com.janreins.habitude.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AutoBackupNamesTest {
    private val today = LocalDate.of(2026, 10, 11)

    @Test
    fun finalNamesAcceptBothProviderSuffixes() {
        listOf(
            "habitude-auto-2026-10-11.json",
            "habitude-auto-2026-10-11 (1).json",
            "habitude-auto-2026-10-11.json (1)",
            "habitude-auto-2026-10-11 (23).json",
        ).forEach { assertEquals(it, today, Backup.autoBackupDate(it)) }
    }

    @Test
    fun tempNamesAcceptUniqueRunsAndProviderSuffixes() {
        listOf(
            "habitude-auto-2026-10-11.json.partial",
            "habitude-auto-2026-10-11.json (1).partial",
            "habitude-auto-2026-10-11.json.partial (1)",
            "habitude-auto-2026-10-11 (1).json.partial",
            "habitude-auto-2026-10-11.json.1791727500000.partial",
            "habitude-auto-2026-10-11.json.1791727500000.partial (1)",
            "habitude-auto-2026-10-11.json.1791727500000 (1).partial",
            "habitude-auto-2026-10-11.json (1).1791727500000.partial",
        ).forEach {
            assertEquals(it, today, Backup.autoTempDate(it))
            assertNull(it, Backup.autoBackupDate(it))
            assertTrue(Backup.autoBackupsToDelete(listOf(it), keep = 0).isEmpty())
        }
        assertEquals("habitude-auto-2026-10-11.json.123.partial", Backup.autoTempFileName(Backup.autoFileName(today), 123))
    }

    @Test
    fun unrelatedAndInvalidNamesAreNeverTouched() {
        val names = listOf(
            "habitude-backup-2026-10-11.json",
            "holiday.partial",
            "habitude-auto-2026-02-30.json",
            "habitude-auto-2026-02-30.json.partial",
            "habitude-auto-2026-10-11.json.partial.json",
            "habitude-auto-2026-10-11.json.extra",
            "habitude-auto-2026-10-11.json (0)",
            "habitude-auto-2026-10-11.json (-1)",
            "habitude-auto-2026-10-11.json (x)",
            "habitude-auto-2026-10-11.json.random.partial",
            "habitude-auto-2026-10-11 (1).json (2)",
        )
        names.forEach {
            assertNull(it, Backup.autoBackupDate(it))
            assertNull(it, Backup.autoTempDate(it))
        }
        assertTrue(Backup.autoBackupsToDelete(names, keep = 0).isEmpty())
        assertTrue(Backup.autoTempsToDelete(names).isEmpty())
        assertTrue(Backup.autoBackupDuplicatesToDelete(names, Backup.autoFileName(today)).isEmpty())
        assertNull(Backup.autoTempDate(Backup.autoFileName(today)))
    }

    @Test
    fun retentionCountsDatesAndDeletesEveryOlderSuffixedCopy() {
        val kept = listOf(
            "habitude-auto-2026-10-11.json",
            "habitude-auto-2026-10-11 (1).json",
            "habitude-auto-2026-10-11.json (2)",
            "habitude-auto-2026-10-10.json (1)",
            "habitude-auto-2026-10-09 (1).json",
            "habitude-auto-2026-10-08.json",
        )
        val older = listOf(
            "habitude-auto-2026-10-07.json",
            "habitude-auto-2026-10-07 (1).json",
            "habitude-auto-2026-10-07.json (2)",
            "habitude-auto-2026-10-06.json (1)",
        )
        assertEquals(older, Backup.autoBackupsToDelete(kept + older))
        assertTrue(Backup.autoBackupsToDelete(kept).isEmpty())
        assertEquals((kept + older).toSet(), Backup.autoBackupsToDelete(kept + older, keep = 0).toSet())
    }

    @Test
    fun successfulWriteRemovesOnlyOtherFinalCopiesForItsDate() {
        val original = Backup.autoFileName(today)
        val saved = "habitude-auto-2026-10-11 (1).json"
        val otherCopy = "habitude-auto-2026-10-11.json (2)"
        val names = listOf(original, saved, otherCopy, Backup.autoTempFileName(original), Backup.autoFileName(today.minusDays(1)))
        assertEquals(listOf(original, otherCopy), Backup.autoBackupDuplicatesToDelete(names, saved))
        assertTrue(Backup.autoBackupDuplicatesToDelete(names, "holiday.json").isEmpty())
    }

    @Test
    fun recoveryTempsStayUntilAFinalCopyForTheirDateExists() {
        val recovery = Backup.autoTempFileName(Backup.autoFileName(today), 123)
        val olderRecovery = Backup.autoTempFileName(Backup.autoFileName(today.minusDays(1)), 456)
        val olderFinal = Backup.autoFileName(today.minusDays(2))
        assertTrue(Backup.autoTempsToDelete(listOf(recovery, olderRecovery, olderFinal)).isEmpty())
        assertEquals(
            listOf(recovery),
            Backup.autoTempsToDelete(listOf(recovery, olderRecovery, olderFinal, "habitude-auto-2026-10-11.json (1)")),
        )
    }

    @Test
    fun noRenameSupportUsesFallback() {
        assertEquals(Backup.AutoWriteStrategy.FALLBACK_COPY, Backup.autoWriteStrategy(false, false, false))
        assertEquals(Backup.AutoWriteStrategy.FALLBACK_COPY, Backup.autoWriteStrategy(false, true, true))
    }

    @Test
    fun failedRenameUsesFallback() {
        assertEquals(Backup.AutoWriteStrategy.FALLBACK_COPY, Backup.autoWriteStrategy(true, false, false))
    }

    @Test
    fun wrongRenameNameUsesFallback() {
        assertEquals(Backup.AutoWriteStrategy.FALLBACK_COPY, Backup.autoWriteStrategy(true, true, false))
    }

    @Test
    fun successfulRenameFinishesTheWrite() {
        assertEquals(Backup.AutoWriteStrategy.RENAME_DONE, Backup.autoWriteStrategy(true, true, true))
    }
}
