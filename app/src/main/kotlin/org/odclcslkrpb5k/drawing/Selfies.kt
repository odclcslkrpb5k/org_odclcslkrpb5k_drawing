package org.odclcslkrpb5k.drawing

import android.app.Activity
import android.os.Bundle
import android.util.Log
import org.odclcslkrpb5k.drawing.adapters.Camera
import org.odclcslkrpb5k.drawing.adapters.PageStore
import org.odclcslkrpb5k.drawing.adapters.PhotoStore
import org.odclcslkrpb5k.drawing.adapters.StoreError
import org.odclcslkrpb5k.drawing.core.History
import org.odclcslkrpb5k.drawing.core.Outcome
import org.odclcslkrpb5k.drawing.core.PageId

/**
 * A new page on a photo (the selfie stretch): the camera app takes the photo, it becomes the page's
 * photo, the page is saved at once (so a photo she does not draw on still shows on home), and the
 * page opens. The page waiting for its photo is kept across the app being stopped while the camera
 * is in front ([save], [restore]). Backing out of the camera leaves nothing behind.
 */
class Selfies(
    private val activity: Activity,
    // Providers, not stores: a store reads the app's files directory, which must first happen on
    // the disk thread (StrictMode ends a debug build that does it on the main thread).
    private val photos: () -> PhotoStore,
    private val store: () -> PageStore,
    private val open: (PageId) -> Unit,
) {
    private var waiting: PageId? = null

    val available: Boolean
        get() = Camera.available(activity)

    /** Opens the camera for page [id]; a tap while a camera is already on its way does nothing. */
    fun take(id: PageId) {
        if (waiting != null) return
        waiting = id
        PageDisk.run(
            { Camera.uriFor(activity, photos().pending(id)) },
            failed = Outcome.Err("unexpected"),
        ) { uri ->
            val launched =
                uri is Outcome.Ok && Camera.launch(activity, uri.value, REQUEST) is Outcome.Ok
            if (!launched) waiting = null
        }
    }

    /** An activity answered [request]: if it is the camera's, the photo becomes a page. */
    fun answered(request: Int, result: Int) {
        val id = waiting
        if (request != REQUEST || id == null) return
        waiting = null
        if (result != Activity.RESULT_OK) {
            PageDisk.run({ photos().discard(id) }, failed = Unit) {}
            return
        }
        PageDisk.run(
            {
                when (val adopted = photos().adopt(id)) {
                    is Outcome.Ok ->
                        store().save(id, History()).also {
                            // A photo with no page would never be shown or cleared.
                            if (it is Outcome.Err) photos().forget(id)
                        }
                    is Outcome.Err -> adopted
                }
            },
            failed = Outcome.Err(StoreError.IO),
        ) { done ->
            if (done is Outcome.Ok) {
                if (!activity.isDestroyed) open(id)
            } else {
                Log.w(TAG, "selfie page not made")
            }
        }
    }

    fun save(out: Bundle) {
        waiting?.let { out.putString(KEY, it.value) }
    }

    /**
     * Picks up a page still waiting for its photo; with none waiting, camera files left behind (by
     * a crash, or a camera that never answered) are cleared.
     */
    fun restore(saved: Bundle?) {
        waiting = saved?.getString(KEY)?.let(PageId::parse)
        if (waiting == null) PageDisk.run({ photos().sweep() }, failed = Unit) {}
    }

    private companion object {
        const val TAG = "Selfies"
        const val REQUEST = 7
        const val KEY = "selfie"
    }
}
