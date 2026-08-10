package com.matrixmessenger.core.extensions

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.imageLoader
import org.matrix.android.sdk.api.session.Session

/**
 * Extension functions for Context
 */

/**
 * Get dp value as pixels
 */
val Number.dpToPx: Int
    get() = (this.toFloat() * Resources.getSystem().displayMetrics.density).toInt()

/**
 * Get px value as dp
 */
val Number.pxToDp: Dp
    get() = (this.toFloat() / Resources.getSystem().displayMetrics.density).dp

/**
 * Convert dp to pixels using context
 */
fun Context.dpToPx(dp: Float): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        resources.displayMetrics
    ).toInt()
}

/**
 * Convert pixels to dp using context
 */
fun Context.pxToDp(px: Int): Float {
    return px / resources.displayMetrics.density
}

/**
 * Get color from resource ID
 */
fun Context.getColorCompat(resourceId: Int): Int {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        getColor(resourceId)
    } else {
        @Suppress("DEPRECATION")
        resources.getColor(resourceId)
    }
}

/**
 * Get drawable from resource ID
 */
fun Context.getDrawableCompat(resourceId: Int): android.graphics.drawable.Drawable? {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
        getDrawable(resourceId)
    } else {
        @Suppress("DEPRECATION")
        resources.getDrawable(resourceId)
    }
}

/**
 * Check if network is available
 */
fun Context.isNetworkAvailable(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
    return connectivityManager?.activeNetworkInfo?.isConnected == true
}

/**
 * Get session from context if available
 */
fun Context.getActiveSession(): Session? {
    return try {
        val matrix = org.matrix.android.sdk.api.Matrix.getInstance(this)
        matrix.getAllSessions().firstOrNull { it.sessionParams.userId.isNotEmpty() }
    } catch (e: Exception) {
        null
    }
}

/**
 * Clear app cache
 */
fun Context.clearCache() {
    try {
        cacheDir.deleteRecursively()
        codeCacheDir?.deleteRecursively()
    } catch (e: Exception) {
        // Ignore errors
    }
}

/**
 * Get app version name
 */
fun Context.getAppVersionName(): String {
    return try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }
}

/**
 * Get app version code
 */
fun Context.getAppVersionCode(): Long {
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(packageName, 0).longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
        }
    } catch (e: Exception) {
        1L
    }
}
