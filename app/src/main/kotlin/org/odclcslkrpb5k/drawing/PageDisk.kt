package org.odclcslkrpb5k.drawing

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

/**
 * The one thread that reads and writes pages, for the whole app, never shut down. Saves, loads and
 * the gallery's reads queue here in the order they were asked for, so a page reopened at once is
 * read only after its last save is written, and two screens can never write one page at once.
 */
object PageDisk {
    private val disk = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /**
     * Runs [work] on the disk thread, then hands its result to [then] on the main thread. This is
     * the thread's catch-all (ER-5): an exception [work] did not expect is logged and counted, and
     * [failed] is handed on instead, so the caller always hears back (a save is never left in
     * flight). An Error (out of memory) still ends the process.
     */
    fun <T> run(work: () -> T, failed: T, then: (T) -> Unit) {
        disk.execute {
            val result =
                try {
                    work()
                } catch (e: Exception) {
                    unexpected += 1
                    Log.e(
                        TAG,
                        "unexpected ${e.javaClass.simpleName} on the disk thread ($unexpected so far)",
                    )
                    failed
                }
            main.post { then(result) }
        }
    }

    @Volatile private var unexpected = 0

    private const val TAG = "PageDisk"
}
