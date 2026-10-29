package com.poshan.photoprint.core.export

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.poshan.photoprint.core.storage.AppPreferenceManager

/**
 * BroadcastReceiver triggered by Android Intent.createChooser when user selects an app.
 * Saves the chosen package into AppPreferenceManager so future exports open directly in that app.
 */
class AppChooserReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val chosenComponent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT)
        }

        val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE)
        val packageName = chosenComponent?.packageName

        if (!packageName.isNullOrBlank()) {
            if (mimeType == "application/pdf") {
                AppPreferenceManager.setPreferredPdfPackage(context, packageName)
            } else if (mimeType == "image/jpeg" || mimeType?.startsWith("image/") == true) {
                AppPreferenceManager.setPreferredImagePackage(context, packageName)
            }
        }
    }

    companion object {
        const val ACTION_VIEWER_CHOSEN = "com.poshan.photoprint.ACTION_VIEWER_CHOSEN"
        const val EXTRA_MIME_TYPE = "extra_mime_type"
    }
}
