package com.inkbridge.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class InkBridgeUpdate(
    val versionCode: Int,
    val tagName: String,
    val apkUrl: String,
    val digest: String?,
    val releaseNotes: String
)

/** Public GitHub Releases updater. APK installation always remains under Android's user approval. */
object UpdateManager {
    private const val RELEASE_URL = "https://api.github.com/repos/ahmed1-khaled/InkBridge/releases/latest"
    private const val APK_ASSET = "InkBridge-Android.apk"
    private var pendingApk: File? = null

    fun check(context: Context, callback: (InkBridgeUpdate?, String?) -> Unit) {
        Thread {
            var result: InkBridgeUpdate? = null
            var error: String? = null
            try {
                val connection = (URL(RELEASE_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "InkBridge-Android")
                    instanceFollowRedirects = true
                }
                try {
                    if (connection.responseCode != 200) throw IllegalStateException("GitHub returned HTTP ${connection.responseCode}")
                    val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) {
                        throw IllegalStateException("The latest GitHub release is not a stable release")
                    }
                    val tag = release.optString("tag_name")
                    val remoteCode = tag.substringAfterLast('.').toIntOrNull()
                        ?: throw IllegalStateException("The release tag does not contain an InkBridge build number")
                    val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
                        context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo(context.packageName, 0)
                    }
                    val localCode = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode.toInt() else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode
                    }
                    if (remoteCode > localCode) {
                        val assets = release.optJSONArray("assets") ?: throw IllegalStateException("Release has no assets")
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.optString("name") == APK_ASSET) {
                                result = InkBridgeUpdate(
                                    remoteCode,
                                    tag,
                                    asset.getString("browser_download_url"),
                                    asset.optString("digest").takeIf { it.startsWith("sha256:") }?.substringAfter("sha256:"),
                                    release.optString("body")
                                )
                                break
                            }
                        }
                        if (result == null) throw IllegalStateException("The latest release does not include the Android APK")
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not check for updates"
            }
            Handler(Looper.getMainLooper()).post { callback(result, error) }
        }.start()
    }

    fun downloadAndInstall(activity: Activity, update: InkBridgeUpdate, callback: (String) -> Unit) {
        Thread {
            try {
                val folder = File(activity.cacheDir, "updates")
                if (!folder.exists() && !folder.mkdirs()) throw IllegalStateException("Could not create the update folder")
                val apk = File(folder, "InkBridge-${update.versionCode}.apk")
                val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 45000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "InkBridge-Android")
                }
                try {
                    if (connection.responseCode !in 200..299) throw IllegalStateException("APK download returned HTTP ${connection.responseCode}")
                    connection.inputStream.use { input ->
                        apk.outputStream().use { output -> input.copyTo(output) }
                    }
                } finally {
                    connection.disconnect()
                }
                if (apk.length() < 100_000) throw IllegalStateException("The downloaded APK looks incomplete")
                if (update.digest != null) {
                    val actual = MessageDigest.getInstance("SHA-256").digest(apk.readBytes()).joinToString("") { "%02x".format(it) }
                    if (!actual.equals(update.digest, ignoreCase = true)) {
                        apk.delete()
                        throw IllegalStateException("APK checksum verification failed")
                    }
                }
                Handler(Looper.getMainLooper()).post {
                    callback("Downloaded InkBridge ${update.tagName}. Opening Android installer…")
                    installOrRequestPermission(activity, apk)
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post { callback("Update failed: ${e.message ?: "download error"}") }
            }
        }.start()
    }

    fun retryPendingInstall(activity: Activity) {
        val file = pendingApk ?: return
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) return
        pendingApk = null
        installApk(activity, file)
    }

    private fun installOrRequestPermission(activity: Activity, apk: File) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            pendingApk = apk
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            activity.startActivity(intent)
            android.widget.Toast.makeText(activity, "Allow InkBridge to install updates, then return here.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        installApk(activity, apk)
    }

    @Suppress("DEPRECATION")
    private fun installApk(activity: Activity, apk: File) {
        try {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(activity, "Could not open Android installer: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
