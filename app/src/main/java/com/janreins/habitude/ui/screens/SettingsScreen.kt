package com.janreins.habitude.ui.screens

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import com.janreins.habitude.HabitudeApplication
import com.janreins.habitude.backup.AutoBackup
import com.janreins.habitude.backup.SafetyBackup
import com.janreins.habitude.domain.Backup
import com.janreins.habitude.domain.BackupContents
import com.janreins.habitude.domain.BackupException
import com.janreins.habitude.notify.Notifications
import com.janreins.habitude.ui.lock.Biometrics
import com.janreins.habitude.ui.lock.PinPad
import com.janreins.habitude.ui.lock.findFragmentActivity
import com.janreins.habitude.ui.plural
import com.janreins.habitude.ui.rememberNotificationPermissionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as HabitudeApplication
    var nudge by remember { mutableStateOf(app.settings.eveningNudge) }
    var canPost by remember { mutableStateOf(Notifications.canPost(context)) }
    val openSystemSettings = {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    val askPermission = rememberNotificationPermissionRequest { granted ->
        canPost = Notifications.canPost(context)
        // The system stops showing the prompt after two refusals; send people to settings instead.
        if (!granted) openSystemSettings()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canPost = Notifications.canPost(context) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))

        if (!canPost) {
            SettingsCard {
                Text("Notifications are off", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Reminders and the evening streak check can't reach you until notifications are allowed for Habitude.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = {
                    if (Notifications.needsRuntimePermission(context)) askPermission() else openSystemSettings()
                }) { Text("Turn on notifications") }
            }
        }

        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Evening streak check", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "At 8pm, a heads-up if a streak of two days or more is about to break.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = nudge,
                    onCheckedChange = { on ->
                        nudge = on
                        app.settings.eveningNudge = on
                        app.reminders.scheduleNudge(on)
                        if (on) askPermission()
                    },
                )
            }
            Text(
                "Per-habit reminders are set when you add or edit a habit.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AppLockCard(app)

        BackupCard(app, onNudgeChanged = { nudge = it })

        SettingsCard {
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text(
                "Habitude${version?.let { " $it" }.orEmpty()}. Build the habits you want and break the ones you don't. " +
                    "Everything stays on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

private enum class PinStep { VerifyToDisable, VerifyToChange, Choose, Confirm }

@Composable
private fun AppLockCard(app: HabitudeApplication) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPin by remember { mutableStateOf(app.settings.hasPin) }
    var step by remember { mutableStateOf<PinStep?>(null) }
    var chosen by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var resetKey by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var biometric by remember { mutableStateOf(app.settings.biometricUnlock) }
    // Checked again on return, in case a fingerprint was just added in Android settings.
    var canUseBiometrics by remember { mutableStateOf(Biometrics.available(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canUseBiometrics = Biometrics.available(context) }

    fun start(next: PinStep) {
        error = null
        chosen = ""
        step = next
    }

    fun onPin(pin: String) {
        resetKey++
        when (step) {
            PinStep.Choose -> {
                chosen = pin
                error = null
                step = PinStep.Confirm
            }
            PinStep.Confirm -> {
                if (pin == chosen) {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.Default) { app.settings.setPin(pin) }
                        busy = false
                        hasPin = true
                        step = null
                        Toast.makeText(context, "PIN set. Habitude will ask for it when you open the app.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    error = "Those didn't match. Choose your PIN again."
                    step = PinStep.Choose
                }
            }
            PinStep.VerifyToDisable, PinStep.VerifyToChange -> {
                val current = step
                busy = true
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { app.settings.checkPin(pin) }
                    busy = false
                    when {
                        !ok -> error = "That's not your current PIN."
                        current == PinStep.VerifyToDisable -> {
                            app.settings.clearPin()
                            hasPin = false
                            biometric = false
                            step = null
                            Toast.makeText(context, "App lock is off.", Toast.LENGTH_SHORT).show()
                        }
                        else -> start(PinStep.Choose)
                    }
                }
            }
            null -> Unit
        }
    }

    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("App lock", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Ask for a 6-digit PIN when you open Habitude, or come back to it after a minute away.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = hasPin,
                onCheckedChange = { on -> start(if (on) PinStep.Choose else PinStep.VerifyToDisable) },
            )
        }
        if (hasPin && canUseBiometrics) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fingerprint or face", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Unlock with your fingerprint or face. The PIN still works too.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = biometric,
                    onCheckedChange = { on ->
                        if (!on) {
                            biometric = false
                            app.settings.biometricUnlock = false
                        } else {
                            // Check it works before relying on it.
                            context.findFragmentActivity()?.let { activity ->
                                Biometrics.prompt(activity, "Turn on fingerprint or face unlock") {
                                    biometric = true
                                    app.settings.biometricUnlock = true
                                }
                            }
                        }
                    },
                )
            }
        }
        if (hasPin) {
            OutlinedButton(onClick = { start(PinStep.VerifyToChange) }) { Text("Change PIN") }
        }
    }

    step?.let { current ->
        Dialog(
            onDismissRequest = { step = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier
                        .systemBarsPadding()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        IconButton(onClick = { step = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    PinPad(
                        title = when (current) {
                            PinStep.VerifyToDisable, PinStep.VerifyToChange -> "Enter your current PIN"
                            PinStep.Choose -> "Choose a 6-digit PIN"
                            PinStep.Confirm -> "Enter it once more"
                        },
                        message = error ?: if (current == PinStep.Choose) {
                            "If you forget it, the only way back in erases your habits. Export a backup first."
                        } else {
                            null
                        },
                        isError = error != null,
                        enabled = !busy,
                        resetKey = resetKey,
                        onComplete = ::onPin,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupCard(app: HabitudeApplication, onNudgeChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<BackupContents?>(null) }
    var currentCount by remember { mutableIntStateOf(0) }
    var restoring by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var safetyTime by remember { mutableLongStateOf(SafetyBackup.madeAt(context.filesDir)) }
    val toast = { text: String -> Toast.makeText(context, text, Toast.LENGTH_LONG).show() }

    suspend fun replace(contents: BackupContents, restore: Boolean) {
        try {
            val current = app.repository.snapshot()
            val keptPrevious = !restore && current.isNotEmpty()
            if (keptPrevious) {
                val saved = runCatching {
                    withContext(Dispatchers.IO) {
                        SafetyBackup.write(
                            context.filesDir,
                            Backup.export(current, app.settings.eveningNudge, LocalDateTime.now()),
                        )
                    }
                }
                if (saved.isFailure) {
                    toast("Couldn't save your previous data. Import stopped; nothing was changed.")
                    return
                }
                safetyTime = SafetyBackup.madeAt(context.filesDir)
            }
            val done = runCatching { app.repository.replaceAll(contents.habits) }
            if (done.isFailure) {
                toast("${if (restore) "Restore" else "Import"} failed. Nothing was changed.")
                return
            }
            contents.eveningNudge?.let {
                app.settings.eveningNudge = it
                onNudgeChanged(it)
            }
            current.forEach { app.reminders.cancel(it.habit.id) }
            app.rescheduleReminders()
            val message = "${if (restore) "Restored" else "Imported"} ${plural(contents.habits.size, "habit")}."
            toast(if (keptPrevious) "$message Your previous data is saved; restore it from Settings → Backup." else message)
        } finally {
            busy = false
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = runCatching {
                withContext(Dispatchers.IO) {
                    val text = Backup.export(app.repository.snapshot(), app.settings.eveningNudge, LocalDateTime.now())
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }
            }
            toast(if (saved.isSuccess) "Backup saved." else "Couldn't save the backup.")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                }
                val contents = withContext(Dispatchers.Default) { Backup.parse(text) }
                currentCount = app.repository.snapshot().size
                restoring = false
                pending = contents
            } catch (e: BackupException) {
                toast(e.message.orEmpty())
            } catch (e: Exception) {
                toast("Couldn't read that file.")
            }
        }
    }

    SettingsCard {
        Text("Backup", style = MaterialTheme.typography.titleMedium)
        Text(
            "Save all your habits and history to a file, then import it on a new phone or after reinstalling.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = { exportLauncher.launch("habitude-backup-${LocalDate.now()}.json") }, enabled = !busy) {
                Text("Export")
            }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, enabled = !busy) {
                Text("Import")
            }
        }
        if (safetyTime != 0L) {
            val made = Instant.ofEpochMilli(safetyTime).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
            Text("Previous data saved $made.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val contents = withContext(Dispatchers.IO) { Backup.parse(SafetyBackup.read(context.filesDir)) }
                            currentCount = app.repository.snapshot().size
                            restoring = true
                            pending = contents
                        } catch (e: BackupException) {
                            toast(e.message.orEmpty())
                        } catch (e: Exception) {
                            toast("Couldn't read the previous data.")
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text("Restore data from before the last import") }
        }
    }

    AutoBackupCard(app)

    pending?.let { contents ->
        val made = if (restoring) {
            Instant.ofEpochMilli(safetyTime).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
        } else runCatching {
            LocalDateTime.parse(contents.exportedAt).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
        }.getOrDefault(contents.exportedAt)
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Replace your data?") },
            text = {
                Text(
                    if (restoring) {
                        "This restores your data as it was just before the import on $made. " +
                            "It has ${plural(contents.habits.size, "habit")} and replaces " +
                            "the ${plural(currentCount, "habit")} on this phone and all their history."
                    } else {
                        "This backup from $made has ${plural(contents.habits.size, "habit")}. Importing replaces " +
                            "the ${plural(currentCount, "habit")} on this phone and all their history."
                    },
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    if (busy) return@TextButton
                    val restore = restoring
                    pending = null
                    busy = true
                    scope.launch { replace(contents, restore) }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

/** Weekly backups to a folder the user picks, such as Downloads or a synced Drive folder. */
@Composable
private fun AutoBackupCard(app: HabitudeApplication) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var folder by remember { mutableStateOf(app.settings.autoBackupFolder?.let(Uri::parse)) }
    var folderName by remember { mutableStateOf<String?>(null) }
    var last by remember { mutableLongStateOf(app.settings.lastAutoBackup) }
    var failed by remember { mutableStateOf(app.settings.autoBackupFailed) }
    var accessLost by remember { mutableStateOf(app.settings.autoBackupAccessLost) }
    var busy by remember { mutableStateOf(false) }
    val toast = { text: String -> Toast.makeText(context, text, Toast.LENGTH_LONG).show() }

    LaunchedEffect(folder) {
        folderName = folder?.let { withContext(Dispatchers.IO) { AutoBackup.folderName(context, it) } }
    }
    // A backup may have run in the background since this screen was last shown.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        last = app.settings.lastAutoBackup
        failed = app.settings.autoBackupFailed
        accessLost = app.settings.autoBackupAccessLost
    }

    fun backUpNow() {
        busy = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { AutoBackup.runNow(context) }
            busy = false
            last = app.settings.lastAutoBackup
            failed = app.settings.autoBackupFailed
            accessLost = app.settings.autoBackupAccessLost
            toast(when (outcome) {
                AutoBackup.Outcome.Success -> "Backup saved."
                AutoBackup.Outcome.NoFolder -> "Pick a backup folder first."
                AutoBackup.Outcome.AccessLost -> "Habitude lost access to the backup folder. Pick it again."
                is AutoBackup.Outcome.Failed -> "Couldn't save the backup. Try again later."
            })
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching { AutoBackup.keepAccess(context, uri) }.isSuccess
        if (!ok) {
            toast("Habitude can't keep access to that folder. Try another one.")
            return@rememberLauncherForActivityResult
        }
        folder?.takeIf { it != uri }?.let { AutoBackup.releaseAccess(context, it) }
        app.settings.autoBackupFolder = uri.toString()
        app.settings.autoBackupAccessLost = false
        accessLost = false
        Notifications.cancel(context, Notifications.BACKUP_NOTIFICATION_ID)
        folder = uri
        AutoBackup.schedule(context, true)
        backUpNow()
    }

    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Weekly backup", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Save a backup to a folder you choose every week. The newest ${Backup.AUTO_KEEP} are kept.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = folder != null,
                onCheckedChange = { on ->
                    if (on) {
                        pickFolder.launch(null)
                    } else {
                        folder?.let { AutoBackup.releaseAccess(context, it) }
                        app.settings.autoBackupFolder = null
                        app.settings.autoBackupFailed = false
                        app.settings.autoBackupAccessLost = false
                        Notifications.cancel(context, Notifications.BACKUP_NOTIFICATION_ID)
                        accessLost = false
                        AutoBackup.schedule(context, false)
                        folder = null
                        failed = false
                    }
                },
            )
        }
        if (folder != null) {
            val lastLine = if (last == 0L) {
                "Not saved yet."
            } else {
                "Last saved " + Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("d MMM, HH:mm")) + "."
            }
            Text(
                "To ${folderName ?: "the chosen folder"}. $lastLine",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (accessLost || failed) {
                Text(
                    if (accessLost) {
                        "Habitude lost access to the backup folder. Pick it again to keep weekly backups going."
                    } else {
                        "The last backup couldn't be saved. Try again later."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = ::backUpNow, enabled = !busy) { Text("Back up now") }
                OutlinedButton(onClick = { pickFolder.launch(folder) }, enabled = !busy) { Text("Change folder") }
            }
        }
    }
}
