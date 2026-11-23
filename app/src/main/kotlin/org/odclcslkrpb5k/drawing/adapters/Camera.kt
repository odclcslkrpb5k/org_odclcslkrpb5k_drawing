package org.odclcslkrpb5k.drawing.adapters

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import org.odclcslkrpb5k.drawing.core.Outcome

/**
 * The tablet's own camera app takes the photo: the app asks for no camera permission at all. The
 * photo is written straight into the app's private files through its FileProvider, not the shared
 * gallery; only her drawings' gallery copies (Gallery), which include it, go there. Under Samsung
 * Kids there may be no camera app the app can reach; then [available] is false and home shows no
 * camera tile, rather than a tile that fails.
 */
object Camera {
    fun available(context: Context): Boolean =
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null

    /**
     * The address the camera app writes [file] through (disk thread: the provider reads the app's
     * files directory the first time).
     */
    fun uriFor(context: Context, file: File): Outcome<Uri, String> =
        try {
            Outcome.Ok(FileProvider.getUriForFile(context, "${context.packageName}.photos", file))
        } catch (e: IllegalArgumentException) {
            Outcome.Err("no uri: ${e.javaClass.simpleName}").also { Log.w(TAG, it.error) }
        }

    /**
     * Opens the camera to write its photo to [uri]; its answer comes to [activity] by [request].
     */
    fun launch(activity: Activity, uri: Uri, request: Int): Outcome<Unit, String> =
        try {
            val intent =
                Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    .putExtra(MediaStore.EXTRA_OUTPUT, uri)
                    // Hints for the front camera (a selfie); camera apps may ignore them.
                    .putExtra("android.intent.extras.CAMERA_FACING", 1)
                    .putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                    .putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
                    .addFlags(
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
            activity.startActivityForResult(intent, request)
            Outcome.Ok(Unit)
        } catch (e: ActivityNotFoundException) {
            Outcome.Err("no camera: ${e.javaClass.simpleName}").also { Log.w(TAG, it.error) }
        }

    private const val TAG = "Camera"
}
