package org.odclcslkrpb5k.drawing.adapters

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import java.util.concurrent.Executors

/**
 * Ink loads its native library from disk the first time one of its classes is used. Done on the
 * main thread that is a stalled frame (and a StrictMode death in debug builds), so it is loaded on
 * a background thread when the app starts, and screens that draw wait for [whenLoaded].
 */
object InkLoader {
    private val main = Handler(Looper.getMainLooper())
    private val waiting = mutableListOf<() -> Unit>()
    private var loaded = false
    private var started = false

    /** Starts loading; called once, from the main thread, when the app starts. */
    fun start() {
        if (started) return
        started = true
        val loader = Executors.newSingleThreadExecutor()
        loader.execute {
            // The renderer's class initializer loads Ink's native library (NativeLoader is not on
            // the compile classpath); this instance is discarded.
            CanvasStrokeRenderer.create()
            // Ink's front buffer uses androidx.graphics, whose JNI bindings load their own library
            // when first touched, from a surface callback on the main thread.
            initialize(GRAPHICS_JNI)
            main.post {
                loaded = true
                waiting.forEach { it() }
                waiting.clear()
            }
        }
        loader.shutdown()
    }

    /**
     * Initializes a class by name; a class that is gone is logged, and StrictMode reports the rest.
     */
    private fun initialize(name: String) {
        try {
            Class.forName(name, true, InkLoader::class.java.classLoader)
        } catch (_: ClassNotFoundException) {
            Log.w(TAG, "preload class not found: $name")
        }
    }

    /** Runs [block] on the main thread once Ink is loaded: at once if it already is. */
    fun whenLoaded(block: () -> Unit) {
        if (loaded) block() else waiting += block
    }

    private const val TAG = "InkLoader"
    private const val GRAPHICS_JNI = "androidx.graphics.surface.JniBindings"
}
