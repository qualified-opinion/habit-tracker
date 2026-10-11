package com.janreins.habitude.ui.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.janreins.habitude.domain.Notes
import com.janreins.habitude.ui.lock.LockAwareDialogHost
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Writes or changes the note on one day. Saving an empty note removes it. */
@Composable
fun NoteDialog(
    day: LocalDate,
    initial: String,
    placeholder: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(day) { mutableStateOf(initial) }
    LockAwareDialogHost {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Note for ${day.format(DateTimeFormatter.ofPattern("EEE d MMM"))}") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(Notes.MAX_LENGTH) },
                    placeholder = { Text(placeholder) },
                    supportingText = { Text("${text.length} / ${Notes.MAX_LENGTH}", style = MaterialTheme.typography.bodySmall) },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { onSave(text) }) { Text(if (text.isBlank() && initial.isNotEmpty()) "Remove note" else "Save") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}
