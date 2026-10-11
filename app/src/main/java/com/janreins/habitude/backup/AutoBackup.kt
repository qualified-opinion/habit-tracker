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
                val text = Backup.export(app.repository.snapshot(), app.settings.eveningNudge, LocalDateTime.now())
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

    private fun children(context: Context, folder: Uri): Map<String, String> {
        val children = mutableMapOf<String, String>()
        try {
            context.contentResolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder)),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) children[cursor.getString(1)] = cursor.getString(0)
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
        fun delete(fileName: String) {
            val id = existing[fileName] ?: return
            if (!DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(folder, id))) {
                throw IOException("Couldn't delete the old backup")
            }
        }

        val stale = existing.keys.filter(Backup::isAutoTempFileName)
        // A temp without a final file may be the only complete copy after a failed rename.
        // Keep those until this run has written a complete replacement.
        val safeToDelete = stale.filter { it.removeSuffix(".partial") in existing }
        safeToDelete.forEach(::delete)
        // A generic MIME type stops providers appending .json after the temporary suffix.
        val file = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", Backup.autoTempFileName(name))
            ?: throw IOException("Couldn't create the backup file")
        try {
            val tempName = documentName(context, file)
            if (tempName == null || !Backup.isAutoTempFileName(tempName)) {
                throw IOException("The backup provider changed the temporary file name")
            }
            val output = resolver.openOutputStream(file, "wt") ?: throw IOException("Couldn't open the backup file")
            output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, file) }
            throw e
        }

        (stale - safeToDelete.toSet()).forEach(::delete)
        delete(name)
        // If rename fails after deleting the old file, leave the complete temp in place
        // and fail this run. Retention must not run until the final name is confirmed.
        val renamed = DocumentsContract.renameDocument(resolver, file, name)
            ?: throw IOException("Couldn't rename the backup file")
        if (documentName(context, renamed) != name) throw IOException("The backup provider changed the file name")

        val finalChildren = children(context, folder)
        Backup.autoBackupsToDelete(finalChildren.keys.toList()).forEach { oldName ->
            val oldFile = DocumentsContract.buildDocumentUriUsingTree(folder, finalChildren.getValue(oldName))
            if (!DocumentsContract.deleteDocument(resolver, oldFile)) throw IOException("Couldn't remove the oldest backup")
        }
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (val outcome = AutoBackup.runNow(applicationContext)) {
        AutoBackup.Outcome.Success, AutoBackup.Outcome.NoFolder, AutoBackup.Outcome.AccessLost -> Result.success()
        is AutoBackup.Outcome.Failed -> if (outcome.transient && runAttemptCount < 3) Result.retry() else Result.success()
    }
}
