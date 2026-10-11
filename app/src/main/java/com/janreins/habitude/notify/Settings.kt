package com.janreins.habitude.notify

import android.content.Context
import com.janreins.habitude.domain.Pin

/** Small app preferences kept on the phone. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var eveningNudge: Boolean
        get() = prefs.getBoolean(KEY_NUDGE, true)
        set(value) = prefs.edit().putBoolean(KEY_NUDGE, value).apply()

    val hasPin: Boolean get() = prefs.contains(KEY_PIN_HASH)

    /** Stores a salted hash of [pin], never the PIN itself. */
    fun setPin(pin: String) {
        require(Pin.isValid(pin))
        val salt = Pin.newSalt()
        prefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, Pin.hash(pin, salt))
            .remove(KEY_PIN_FAILURES)
            .remove(KEY_PIN_LOCKED_UNTIL)
            .apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val hash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return Pin.matches(pin, salt, hash)
    }

    /** Wrong PINs in a row, kept so closing the app doesn't reset the wait. */
    var pinFailures: Int
        get() = prefs.getInt(KEY_PIN_FAILURES, 0)
        set(value) = prefs.edit().putInt(KEY_PIN_FAILURES, value).apply()

    /** Wall-clock time (ms) until which PIN entry is paused after too many wrong tries. */
    var pinLockedUntil: Long
        get() = prefs.getLong(KEY_PIN_LOCKED_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_PIN_LOCKED_UNTIL, value).apply()

    /** The milestone already celebrated in a habit's current run, so each shows only once. */
    fun celebratedMilestone(habitId: Long): Int = prefs.getInt(KEY_MILESTONE + habitId, 0)

    fun setCelebratedMilestone(habitId: Long, milestone: Int) {
        if (milestone == celebratedMilestone(habitId)) return
        prefs.edit().putInt(KEY_MILESTONE + habitId, milestone).apply()
    }

    /**
     * False until milestones have been checked once. The first check only takes note of where
     * each habit is, so runs from before milestones existed don't all celebrate at once.
     */
    var milestonesStarted: Boolean
        get() = prefs.getBoolean(KEY_MILESTONES_STARTED, false)
        set(value) = prefs.edit().putBoolean(KEY_MILESTONES_STARTED, value).apply()

    /** Unlock with a fingerprint or face as well as the PIN. Only meaningful while a PIN is set. */
    var biometricUnlock: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    /** The folder picked for automatic weekly backups (a document tree URI), or null when off. */
    var autoBackupFolder: String?
        get() = prefs.getString(KEY_AUTO_BACKUP_FOLDER, null)
        set(value) = prefs.edit().putString(KEY_AUTO_BACKUP_FOLDER, value).apply()

    /** When the last automatic backup was saved (epoch ms), or 0 if never. */
    var lastAutoBackup: Long
        get() = prefs.getLong(KEY_LAST_AUTO_BACKUP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_AUTO_BACKUP, value).apply()

    /** Whether the last automatic backup attempt failed, e.g. because the folder is gone. */
    var autoBackupFailed: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BACKUP_FAILED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BACKUP_FAILED, value).apply()

    /** Remembers lost folder access so the user is notified only once until it is fixed. */
    var autoBackupAccessLost: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BACKUP_ACCESS_LOST, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BACKUP_ACCESS_LOST, value).apply()

    fun clearPin() {
        prefs.edit()
            .remove(KEY_BIOMETRIC)
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_FAILURES)
            .remove(KEY_PIN_LOCKED_UNTIL)
            .apply()
    }

    private companion object {
        const val KEY_NUDGE = "evening_nudge"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_FAILURES = "pin_failures"
        const val KEY_PIN_LOCKED_UNTIL = "pin_locked_until"
        const val KEY_MILESTONE = "milestone_"
        const val KEY_BIOMETRIC = "biometric_unlock"
        const val KEY_AUTO_BACKUP_FOLDER = "auto_backup_folder"
        const val KEY_LAST_AUTO_BACKUP = "last_auto_backup"
        const val KEY_AUTO_BACKUP_FAILED = "auto_backup_failed"
        const val KEY_AUTO_BACKUP_ACCESS_LOST = "auto_backup_access_lost"
        const val KEY_MILESTONES_STARTED = "milestones_started"
    }
}
