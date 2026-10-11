package com.janreins.habitude.backup

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** The private copy kept before the last import, left in place after a restore. */
object SafetyBackup {
    private fun file(filesDir: File): File = File(filesDir, "backups/pre-import.json")

    fun exists(filesDir: File): Boolean = file(filesDir).isFile

    fun timestamp(filesDir: File): Long = if (exists(filesDir)) file(filesDir).lastModified() else 0L

    fun read(filesDir: File): String = file(filesDir).readText()

    /** Writes and flushes a complete copy before atomically replacing the previous one. */
    fun write(filesDir: File, text: String) {
        val target = file(filesDir)
        val dir = target.parentFile!!
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Couldn't create the safety backup folder")
        val temp = File.createTempFile("pre-import-", ".tmp", dir)
        try {
            FileOutputStream(temp).use {
                it.write(text.toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            if (!temp.renameTo(target)) throw IOException("Couldn't save the safety backup")
        } finally {
            temp.delete()
        }
    }
}
