package com.janreins.habitude.ui.lock

import android.app.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/** Whether the app underneath the PIN screen is locked. */
val LocalAppLocked = staticCompositionLocalOf { false }

/**
 * Removes separate dialog windows while locked. Keep dialog state outside this host so it
 * returns after authentication; removed windows cannot receive clicks while hidden.
 */
@Composable
fun LockAwareDialogHost(content: @Composable () -> Unit) {
    if (!LocalAppLocked.current) content()
}

/**
 * Keeps an Android picker and its selection while hiding its window on relock. Callbacks
 * must check the supplied guard before acting, including callbacks already queued at relock.
 * Dismissing for the lock does not dismiss the user's pending picker state.
 */
@Composable
fun LockAwarePlatformDialog(onDismiss: () -> Unit, create: (canAct: () -> Boolean) -> Dialog) {
    val locked = rememberUpdatedState(LocalAppLocked.current)
    val dismiss = rememberUpdatedState(onDismiss)
    val active = remember { mutableStateOf(false) }
    val dialog = remember { create { active.value && !locked.value } }
    DisposableEffect(dialog, locked.value) {
        if (!locked.value) {
            active.value = true
            dialog.setOnDismissListener { if (active.value && !locked.value) dismiss.value() }
            dialog.show()
        }
        onDispose {
            active.value = false
            dialog.setOnDismissListener(null)
            dialog.dismiss()
        }
    }
}
