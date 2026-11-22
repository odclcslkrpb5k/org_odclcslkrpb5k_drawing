package org.odclcslkrpb5k.drawing

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import org.odclcslkrpb5k.drawing.adapters.InkLoader

/**
 * In debug builds, disk or network work on the main thread ends the app at once (the frame budget).
 */
class DrawingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        InkLoader.start()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .penaltyDeath()
                    .build()
            )
        }
    }
}
