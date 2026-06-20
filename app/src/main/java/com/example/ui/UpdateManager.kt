package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val latestVersion: String, val downloadUrl: String, val changelog: String) : UpdateState()
    object UpToDate : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
    object PermissionRedirect : UpdateState()
}

class UpdateManager(private val context: Context) {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    private val client = OkHttpClient()

    // Retrieve current version name
    fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    suspend fun checkForUpdates(owner: String, repo: String, token: String? = null) {
        _updateState.value = UpdateState.Checking
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                if (!token.isNullOrEmpty()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }
                val request = requestBuilder.build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _updateState.value = UpdateState.Error("Не удалось получить информацию с GitHub. Код: ${response.code}")
                        return@withContext
                    }

                    val jsonStr = response.body?.string() ?: ""
                    if (jsonStr.isEmpty()) {
                        _updateState.value = UpdateState.Error("Пустой ответ от сервера.")
                        return@withContext
                    }

                    val json = JSONObject(jsonStr)
                    val tagName = json.optString("tag_name", "").trim()
                    val body = json.optString("body", "Нет описания изменений.")
                    
                    if (tagName.isEmpty()) {
                        _updateState.value = UpdateState.Error("Не найден тег версии в релизе.")
                        return@withContext
                    }

                    // Find APK asset
                    val assetsArray = json.optJSONArray("assets")
                    var downloadUrl = ""
                    if (assetsArray != null) {
                        for (i in 0 until assetsArray.length()) {
                            val asset = assetsArray.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk")) {
                                downloadUrl = if (!token.isNullOrEmpty()) {
                                    asset.optString("url", "")
                                } else {
                                    asset.optString("browser_download_url", "")
                                }
                                break
                            }
                        }
                    }

                    if (downloadUrl.isEmpty()) {
                        _updateState.value = UpdateState.Error("В последнем релизе GitHub не найден файл APK.")
                        return@withContext
                    }

                    val currentVersion = getCurrentVersion()
                    if (isNewerVersion(currentVersion, tagName)) {
                        _updateState.value = UpdateState.UpdateAvailable(
                            latestVersion = tagName,
                            downloadUrl = downloadUrl,
                            changelog = body
                        )
                    } else {
                        _updateState.value = UpdateState.UpToDate
                    }
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("Ошибка проверки обновлений: ${e.localizedMessage}")
            }
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currClean = current.replace("v", "", ignoreCase = true).trim()
        val latClean = latest.replace("v", "", ignoreCase = true).trim()
        
        val currParts = currClean.split(".").mapNotNull { it.toIntOrNull() }
        val latParts = latClean.split(".").mapNotNull { it.toIntOrNull() }

        val size = maxOf(currParts.size, latParts.size)
        for (i in 0 until size) {
            val currVal = currParts.getOrNull(i) ?: 0
            val latVal = latParts.getOrNull(i) ?: 0
            if (latVal > currVal) return true
            if (currVal > latVal) return false
        }
        return false
    }

    suspend fun downloadAndInstall(downloadUrl: String, token: String? = null) {
        _updateState.value = UpdateState.Downloading(0f)
        withContext(Dispatchers.IO) {
            try {
                val noRedirectClient = client.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()

                var currentUrl = downloadUrl
                var requestBuilder = Request.Builder().url(currentUrl)
                if (!token.isNullOrEmpty() && currentUrl.contains("api.github.com")) {
                    requestBuilder.header("Authorization", "Bearer $token")
                    requestBuilder.header("Accept", "application/octet-stream")
                }
                var request = requestBuilder.build()
                var response = noRedirectClient.newCall(request).execute()

                var redirectsCount = 0
                while ((response.code == 301 || response.code == 302 || response.code == 303 || response.code == 307 || response.code == 308) && redirectsCount < 5) {
                    val location = response.header("Location") ?: break
                    response.close()

                    currentUrl = location
                    requestBuilder = Request.Builder().url(currentUrl)
                    if (!token.isNullOrEmpty() && currentUrl.contains("api.github.com")) {
                        requestBuilder.header("Authorization", "Bearer $token")
                        requestBuilder.header("Accept", "application/octet-stream")
                    }
                    request = requestBuilder.build()
                    response = noRedirectClient.newCall(request).execute()
                    redirectsCount++
                }

                if (!response.isSuccessful) {
                    _updateState.value = UpdateState.Error("Не удалось скачать APK. Код: ${response.code}")
                    response.close()
                    return@withContext
                }

                val body = response.body
                if (body == null) {
                    _updateState.value = UpdateState.Error("Тело ответа пустое.")
                    response.close()
                    return@withContext
                }

                val totalBytes = body.contentLength()
                val apkFile = File(context.cacheDir, "app-update.apk")
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloadedBytes = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val progress = downloadedBytes.toFloat() / totalBytes
                                _updateState.value = UpdateState.Downloading(progress)
                            }
                        }
                    }
                }
                response.close()

                _updateState.value = UpdateState.ReadyToInstall(apkFile)
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("Ошибка при скачивании: ${e.localizedMessage}")
            }
        }
    }

    fun installApk(apkFile: File) {
        // First check permission on Oreo and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                _updateState.value = UpdateState.PermissionRedirect
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    _updateState.value = UpdateState.Error("Не удалось открыть настройки разрешений. Разрешите установку вручную в настройках системы.")
                }
                return
            }
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Ошибка запуска установщика: ${e.localizedMessage}")
        }
    }
    
    fun resetToIdle() {
        _updateState.value = UpdateState.Idle
    }
}
