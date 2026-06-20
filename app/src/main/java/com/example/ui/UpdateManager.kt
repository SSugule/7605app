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
                
                var request = requestBuilder.build()
                var response = client.newCall(request).execute()
                var actualTokenUsed = token

                if (!response.isSuccessful && (response.code == 401 || response.code == 403) && !token.isNullOrEmpty()) {
                    response.close()
                    // Retry without Authorization Header because the repository might be public
                    val retryRequestBuilder = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                    val retryRequest = retryRequestBuilder.build()
                    response = client.newCall(retryRequest).execute()
                    actualTokenUsed = null
                }

                var isListResponse = false
                var finalResponse = response

                if (!finalResponse.isSuccessful && finalResponse.code == 404) {
                    finalResponse.close()
                    // Fallback to fetch all releases list (for when there are only pre-releases)
                    val listUrl = "https://api.github.com/repos/$owner/$repo/releases"
                    val listRequestBuilder = Request.Builder()
                        .url(listUrl)
                        .header("User-Agent", "Mozilla/5.0")
                    if (!actualTokenUsed.isNullOrEmpty()) {
                        listRequestBuilder.header("Authorization", "Bearer $actualTokenUsed")
                    }
                    var listRequest = listRequestBuilder.build()
                    var listResponse = client.newCall(listRequest).execute()

                    if (!listResponse.isSuccessful && (listResponse.code == 401 || listResponse.code == 403) && !actualTokenUsed.isNullOrEmpty()) {
                        listResponse.close()
                        val retryListRequestBuilder = Request.Builder()
                            .url(listUrl)
                            .header("User-Agent", "Mozilla/5.0")
                        val retryListRequest = retryListRequestBuilder.build()
                        listResponse = client.newCall(retryListRequest).execute()
                        actualTokenUsed = null
                    }
                    finalResponse = listResponse
                    isListResponse = true
                }

                finalResponse.use { resp ->
                    if (!resp.isSuccessful) {
                        _updateState.value = UpdateState.Error("Не удалось получить информацию с GitHub. Код: ${resp.code}")
                        return@withContext
                    }

                    val jsonStr = resp.body?.string() ?: ""
                    if (jsonStr.isEmpty()) {
                        _updateState.value = UpdateState.Error("Пустой ответ от сервера.")
                        return@withContext
                    }

                    val json = if (isListResponse) {
                        val array = org.json.JSONArray(jsonStr)
                        var foundObject: JSONObject? = null
                        for (i in 0 until array.length()) {
                            val candidate = array.getJSONObject(i)
                            if (!candidate.optBoolean("draft", false)) {
                                foundObject = candidate
                                break
                            }
                        }
                        if (foundObject == null) {
                            _updateState.value = UpdateState.Error("В списке релизов GitHub не найдено опубликованных версий.")
                            return@withContext
                        }
                        foundObject
                    } else {
                        JSONObject(jsonStr)
                    }
                    val tagName = json.optString("tag_name", "").trim()
                    val releaseName = json.optString("name", "").trim()
                    val body = json.optString("body", "Нет описания изменений.")
                    
                    if (tagName.isEmpty()) {
                        _updateState.value = UpdateState.Error("Не найден тег версии в релизе.")
                        return@withContext
                    }

                    // Extract the clean version from either tag_name or release name
                    val cleanLatestVersion = extractVersion(tagName, releaseName)

                    // Find APK asset
                    val assetsArray = json.optJSONArray("assets")
                    var downloadUrl = ""
                    if (assetsArray != null) {
                        for (i in 0 until assetsArray.length()) {
                            val asset = assetsArray.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk")) {
                                downloadUrl = if (!actualTokenUsed.isNullOrEmpty()) {
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
                    if (isNewerVersion(currentVersion, cleanLatestVersion)) {
                        val displayVersion = if (!cleanLatestVersion.startsWith("v", ignoreCase = true)) "v$cleanLatestVersion" else cleanLatestVersion
                        _updateState.value = UpdateState.UpdateAvailable(
                            latestVersion = displayVersion,
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

    private fun extractVersion(tag: String, name: String): String {
        val versionRegex = """v?(\d+\.\d+\.\d+)""".toRegex(RegexOption.IGNORE_CASE)
        
        // Try to match in name first if we have a tag named 'latest'
        if (tag.equals("latest", ignoreCase = true)) {
            val match = versionRegex.find(name)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        
        // Try tag_name
        val matchTag = versionRegex.find(tag)
        if (matchTag != null) {
            return matchTag.groupValues[1]
        }
        
        // Fallback to name
        val matchName = versionRegex.find(name)
        if (matchName != null) {
            return matchName.groupValues[1]
        }
        
        return tag
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currClean = current.replace("v", "", ignoreCase = true).trim()
        val latClean = latest.replace("v", "", ignoreCase = true).trim()
        
        val currParts = currClean.split(".").map { it.filter { c -> c.isDigit() } }.map { it.toIntOrNull() ?: 0 }
        val latParts = latClean.split(".").map { it.filter { c -> c.isDigit() } }.map { it.toIntOrNull() ?: 0 }

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
                var actualTokenUsed = token
                if (!actualTokenUsed.isNullOrEmpty() && currentUrl.contains("api.github.com")) {
                    requestBuilder.header("Authorization", "Bearer $actualTokenUsed")
                    requestBuilder.header("Accept", "application/octet-stream")
                }
                var request = requestBuilder.build()
                var response = noRedirectClient.newCall(request).execute()

                if (!response.isSuccessful && (response.code == 401 || response.code == 403) && !actualTokenUsed.isNullOrEmpty()) {
                    response.close()
                    actualTokenUsed = null
                    requestBuilder = Request.Builder().url(currentUrl)
                    if (currentUrl.contains("api.github.com")) {
                        requestBuilder.header("Accept", "application/octet-stream")
                    }
                    request = requestBuilder.build()
                    response = noRedirectClient.newCall(request).execute()
                }

                var redirectsCount = 0
                while ((response.code == 301 || response.code == 302 || response.code == 303 || response.code == 307 || response.code == 308) && redirectsCount < 5) {
                    val location = response.header("Location") ?: break
                    response.close()

                    currentUrl = location
                    requestBuilder = Request.Builder().url(currentUrl)
                    if (!actualTokenUsed.isNullOrEmpty() && currentUrl.contains("api.github.com")) {
                        requestBuilder.header("Authorization", "Bearer $actualTokenUsed")
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
