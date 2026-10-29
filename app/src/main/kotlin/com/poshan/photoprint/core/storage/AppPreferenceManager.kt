package com.poshan.photoprint.core.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user preferences including the remembered default app for viewing exported PDFs and Images.
 * This prevents Android from showing the "Open with..." app chooser dialog every time a report is saved.
 */
object AppPreferenceManager {

    private const val PREFS_NAME = "poshan_app_preferences"
    private const val KEY_PREFERRED_PDF_PKG = "preferred_pdf_viewer_package"
    private const val KEY_PREFERRED_IMAGE_PKG = "preferred_image_viewer_package"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getPreferredPdfPackage(context: Context): String? {
        val pkg = getPrefs(context).getString(KEY_PREFERRED_PDF_PKG, null)
        return if (!pkg.isNullOrBlank() && isPackageInstalled(context, pkg)) pkg else null
    }

    fun setPreferredPdfPackage(context: Context, packageName: String?) {
        getPrefs(context).edit().apply {
            if (packageName != null) {
                putString(KEY_PREFERRED_PDF_PKG, packageName)
            } else {
                remove(KEY_PREFERRED_PDF_PKG)
            }
            apply()
        }
    }

    fun getPreferredImagePackage(context: Context): String? {
        val pkg = getPrefs(context).getString(KEY_PREFERRED_IMAGE_PKG, null)
        return if (!pkg.isNullOrBlank() && isPackageInstalled(context, pkg)) pkg else null
    }

    fun setPreferredImagePackage(context: Context, packageName: String?) {
        getPrefs(context).edit().apply {
            if (packageName != null) {
                putString(KEY_PREFERRED_IMAGE_PKG, packageName)
            } else {
                remove(KEY_PREFERRED_IMAGE_PKG)
            }
            apply()
        }
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearViewerPreferences(context: Context) {
        getPrefs(context).edit().apply {
            remove(KEY_PREFERRED_PDF_PKG)
            remove(KEY_PREFERRED_IMAGE_PKG)
            apply()
        }
    }
}
