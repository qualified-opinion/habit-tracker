package com.janreins.habitude.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.janreins.habitude.HabitudeApplication
import com.janreins.habitude.domain.Backup
import com.janreins.habitude.notify.Notifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Saves a backup file to a folder the user picked, once a week, keeping the newest few.
 * Android's own cloud backup stays off; this only writes where the user chose.
 */
object AutoBackup {
    private const val WORK_NAME = "weekly-backup"
    private val writeMutex = Mutex()

    sealed interface Outcome {
        data object Success : Outcome
        data object NoFolder : Outcome
        data object AccessLost : Outcome
        data class Failed(val transient: Boolean) : Outcome
    }

    /** Starts the weekly backup, or stops it when [on] is false. */
    fun schedule(context: Context, on: Boolean) {
        val work = WorkManager.getInstance(context)
        if (on) {
            work.enqueueUniquePeriodicWork(
                WORK_NAME,
                // UPDATE applies the backoff to existing installs and preserves enqueue time.
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                    .build(),
            )
        } else {
            work.cancelUniqueWork(WORK_NAME)
        }
    }

    /** Keeps access to the picked folder across restarts. */
    fun keepAccess(context: Context, folder: Uri) {
        context.contentResolver.takePersistableUriPermission(
            folder,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    fun releaseAccess(context: Context, folder: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                folder,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    /** The folder's name as the file picker shows it, or null if it can't be read. */
    fun folderName(context: Context, folder: Uri): String? = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    /** Whether Android still holds write access to the chosen tree across restarts. */
    fun hasWriteAccess(context: Context, folder: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == folder && it.isWritePermission }

    /** Marks the loss once, shared by startup and backup attempts. */
    @Synchronized
    fun markAccessLost(context: Context) {
        val settings = (context.applicationContext as HabitudeApplication).settings
        settings.autoBackupFailed = true
        if (settings.autoBackupAccessLost) return
        settings.autoBackupAccessLost = true
        Notifications.showBackupProblem(context)
    }

    /** Writes today's backup, remembering failures and distinguishing lost folder access. */
    suspend fun runNow(context: Context): Outcome = writeMutex.withLock {
        val app = context.applicationContext as HabitudeApplication
        val folder = app.settings.autoBackupFolder?.let(Uri::parse) ?: return@withLock Outcome.NoFolder
        val outcome = try {
            if (!hasWriteAccess(context, folder)) {
                Outcome.AccessLost
            } else {
                requireFolder(context, folder)
                val text = Backup.export(app.repository.snapshot(), app.settings.eveningNudge, LocalDateTime.now(), app.settings.eveningNudgeMinutes)
                write(context, folder, Backup.autoFileName(LocalDate.now()), text)
                Outcome.Success
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Outcome.AccessLost
        } catch (e: IOException) {
            Outcome.Failed(transient = true)
        } catch (e: Exception) {
            Outcome.Failed(transient = false)
        }
        app.settings.autoBackupFailed = outcome != Outcome.Success
        when (outcome) {
            Outcome.Success -> {
                app.settings.lastAutoBackup = System.currentTimeMillis()
                app.settings.autoBackupAccessLost = false
                Notifications.cancel(context, Notifications.BACKUP_NOTIFICATION_ID)
            }
            Outcome.AccessLost -> markAccessLost(context)
            else -> Unit
        }
        outcome
    }

    /** Only a missing tree is access loss; a missing output document may be transient. */
    private fun requireFolder(context: Context, folder: Uri) {
        try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
            context.contentResolver.query(parent, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { if (!it.moveToFirst()) throw FileNotFoundException("Backup folder is gone") }
                ?: throw FileNotFoundException("Backup folder is gone")
        } catch (e: FileNotFoundException) {
            throw SecurityException("Backup folder is gone", e)
        }
    }

    private data class ChildDocument(val name: String, val id: String)

    private fun children(context: Context, folder: Uri): List<ChildDocument> {
        val children = mutableListOf<ChildDocument>()
        try {
            context.contentResolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder)),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) children += ChildDocument(cursor.getString(1), cursor.getString(0))
            } ?: throw IOException("Couldn't list the backup folder")
        } catch (e: FileNotFoundException) {
            throw SecurityException("Backup folder is gone", e)
        }
        return children
    }

    private fun documentName(context: Context, file: Uri): String? =
        context.contentResolver.query(
            file, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

    private fun write(context: Context, folder: Uri, name: String, text: String) {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val existing = children(context, folder)
        fun delete(file: ChildDocument) {
            if (!DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(folder, file.id))) {
                throw IOException("Couldn't delete the old backup")
            }
        }

        // A temp without a final file may be the only complete copy after a failed rename.
        val stale = Backup.autoTempsToDelete(existing.map { it.name }).toSet()
        existing.filter { it.name in stale }.forEach(::delete)
        val day = Backup.autoBackupDate(name) ?: throw IOException("Invalid backup file name")
        // A generic MIME type stops providers appending .json after the temporary suffix.
        val file = DocumentsContract.createDocument(
            resolver, parent, "application/octet-stream", Backup.autoTempFileName(name, System.currentTimeMillis()),
        ) ?: throw IOException("Couldn't create the backup file")
        fun writeText(target: Uri) {
            val output = resolver.openOutputStream(target, "wt") ?: throw IOException("Couldn't open the backup file")
            output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
        try {
            if (documentName(context, file)?.let(Backup::autoTempDate) != day) {
                throw IOException("The backup provider changed the temporary file name")
            }
            writeText(file)
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, file) }
            throw e
        }

        var supportsRename = false
        var renamed: Uri? = null
        var renamedNameAcceptable = false
        var completeCopy = file
        try {
            val flags = resolver.query(
                file, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null,
            )?.use { if (it.moveToFirst()) it.getInt(0) else 0 } ?: 0
            supportsRename = flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0
            if (supportsRename) {
                // With no same-day final, rename first. Otherwise the complete temp protects
                // the old copies while they are removed, and stays if rename and fallback fail.
                existing.filter { Backup.autoBackupDate(it.name) == day }.forEach(::delete)
                renamed = DocumentsContract.renameDocument(resolver, file, name)
                if (renamed != null) {
                    completeCopy = renamed
                    renamedNameAcceptable = documentName(context, renamed)?.let(Backup::autoBackupDate) == day
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            // Some providers advertise rename but reject it, or return an unusable name.
            // The complete copy stays until the fallback has succeeded.
        }

        val saved = when (Backup.autoWriteStrategy(supportsRename, renamed != null, renamedNameAcceptable)) {
            Backup.AutoWriteStrategy.RENAME_DONE -> renamed!!
            Backup.AutoWriteStrategy.FALLBACK_COPY -> {
                val copy = DocumentsContract.createDocument(resolver, parent, "application/json", name)
                    ?: throw IOException("Couldn't create the final backup file")
                try {
                    if (documentName(context, copy)?.let(Backup::autoBackupDate) != day) {
                        throw IOException("The backup provider changed the file name")
                    }
                    writeText(copy)
                } catch (e: Exception) {
                    runCatching { DocumentsContract.deleteDocument(resolver, copy) }
                    // Keep the complete temp even if rename already deleted the previous final.
                    throw e
                }
                if (!DocumentsContract.deleteDocument(resolver, completeCopy)) {
                    throw IOException("Couldn't remove the temporary backup")
                }
                copy
            }
        }

        val finalChildren = children(context, folder)
        val savedName = documentName(context, saved) ?: throw IOException("Couldn't read the final backup name")
        val savedId = DocumentsContract.getDocumentId(saved)
        val names = finalChildren.map { it.name }
        val obsolete = (Backup.autoBackupDuplicatesToDelete(names, savedName) +
            Backup.autoTempsToDelete(names) + Backup.autoBackupsToDelete(names)).toSet()
        finalChildren.filter { it.id != savedId && (it.name == savedName || it.name in obsolete) }.forEach(::delete)
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (val outcome = AutoBackup.runNow(applicationContext)) {
        AutoBackup.Outcome.Success, AutoBackup.Outcome.NoFolder, AutoBackup.Outcome.AccessLost -> Result.success()
        is AutoBackup.Outcome.Failed -> if (outcome.transient && runAttemptCount < 3) Result.retry() else Result.success()
    }
}
