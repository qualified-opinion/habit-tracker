package com.janreins.habitude.backup

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** The private copy kept before the last import, left in place after a restore. */
object SafetyBackup {
    private fun file(filesDir: File): File = File(filesDir, "backups/pre-import.json")

    fun exists(filesDir: File): Boolean = file(filesDir).isFile

    /** The import time, carried with the copy by its atomic rename; reading never changes it. */
    fun madeAt(filesDir: File): Long = if (exists(filesDir)) file(filesDir).lastModified() else 0L

    fun read(filesDir: File): String = file(filesDir).readText()

    /**
     * Writes and flushes a complete copy before atomically replacing the previous one. The time
     * is kept to the whole second, since some phones store file times without milliseconds.
     */
    fun write(filesDir: File, text: String, now: Long = System.currentTimeMillis()) {
        require(now >= 0) { "The import time can't be before 1970" }
        val stamp = now - now % 1000
        val target = file(filesDir)
        val dir = target.parentFile!!
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Couldn't create the safety backup folder")
        val temp = File.createTempFile("pre-import-", ".tmp", dir)
        try {
            FileOutputStream(temp).use {
                it.write(text.toByteArray(Charsets.UTF_8))
                // Set and verify the import time before publishing, so data and time change together.
                if (!temp.setLastModified(stamp) || !sameSecond(temp.lastModified(), stamp)) {
                    throw IOException("Couldn't save the safety backup time")
                }
                it.fd.sync()
            }
            if (!temp.renameTo(target)) throw IOException("Couldn't save the safety backup")
        } finally {
            temp.delete()
        }
    }

    /** Whether a file time read back matches the one set, ignoring milliseconds the phone may drop. */
    internal fun sameSecond(read: Long, set: Long): Boolean = read / 1000 == set / 1000
}
