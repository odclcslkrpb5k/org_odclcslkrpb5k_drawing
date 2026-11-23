package org.odclcslkrpb5k.drawing.core

/** How full Auto Backup's allowance is: fine, nearly full, or over (no backup at all). */
enum class BackupState {
    FINE,
    NEARLY_FULL,
    OVER,
}

/**
 * Google keeps at most [LIMIT] bytes of an app's Auto Backup, and a backup over it is not made at
 * all, without a word to anyone. The parent screen shows how much the drawings use, and warns from
 * [WARN] on, while there is still time to put old drawings in the trash (which is not backed up).
 */
object BackupBudget {
    const val LIMIT: Long = 25L * 1024 * 1024
    const val WARN: Long = 20L * 1024 * 1024

    /**
     * What a backup adds to each file and folder it carries: a full backup is a tar stream, with a
     * 512-byte header per entry and each file padded to 512 bytes; counted generously, so the shown
     * size errs high, never low.
     */
    const val PER_ENTRY: Long = 1024

    fun state(bytes: Long): BackupState =
        when {
            bytes > LIMIT -> BackupState.OVER
            bytes >= WARN -> BackupState.NEARLY_FULL
            else -> BackupState.FINE
        }

    /**
     * Megabytes to one decimal place, rounded up, so a nearly full allowance never looks emptier.
     */
    fun megabytes(bytes: Long): String {
        val tenths = (bytes.coerceAtLeast(0) * TENTHS + MEGABYTE - 1) / MEGABYTE
        return "${tenths / TENTHS}.${tenths % TENTHS}"
    }

    private const val MEGABYTE = 1024L * 1024
    private const val TENTHS = 10
}
