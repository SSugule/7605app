package com.example.ui

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {
    private const val PREFS_NAME = "crash_reporter_prefs"
    private const val KEY_PENDING_CRASH = "pending_crash_json"
    private const val TAG = "CrashReporter"

    private val client = OkHttpClient()

    // Initialize Uncaught Exception Handler
    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrashLocally(context, throwable, true, "Uncaught Exception")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save crash locally", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    // Save crash details locally to SharedPreferences for submission upon next launch
    fun saveCrashLocally(context: Context, throwable: Throwable, isFatal: Boolean, contextInfo: String = "") {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val json = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("exception_class", throwable.javaClass.name)
            put("exception_message", throwable.message ?: "No message")
            put("stack_trace", stackTrace)
            put("is_fatal", isFatal)
            put("context_info", contextInfo)
            put("app_version", getAppVersion(context))
            put("android_version", Build.VERSION.RELEASE)
            put("sdk_int", Build.VERSION.SDK_INT)
            put("brand", Build.BRAND)
            put("model", Build.MODEL)
            put("fingerprint", Build.FINGERPRINT)
        }

        prefs.edit().putString(KEY_PENDING_CRASH, json.toString()).apply()
        Log.i(TAG, "Saved crash log locally (Fatal=$isFatal): ${throwable.message}")
    }

    // Check if there are any locally saved crashes
    fun hasPendingCrash(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_PENDING_CRASH)
    }

    // Load locally saved crash as clear text details
    fun getPendingCrashDetails(context: Context): Map<String, String>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_PENDING_CRASH, null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(json.optLong("timestamp")))
            mapOf(
                "timestamp" to date,
                "exception_class" to json.optString("exception_class"),
                "exception_message" to json.optString("exception_message"),
                "stack_trace" to json.optString("stack_trace"),
                "is_fatal" to json.optBoolean("is_fatal").toString(),
                "context_info" to json.optString("context_info"),
                "app_version" to json.optString("app_version"),
                "device" to "${json.optString("brand")} ${json.optString("model")} (Android ${json.optString("android_version")}, SDK ${json.optString("sdk_int")})"
            )
        } catch (e: Exception) {
            null
        }
    }

    // Get plain raw JSON of pending crash
    fun getPendingCrashRawJson(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PENDING_CRASH, null)
    }

    // Clear locally saved crash
    fun clearPendingCrash(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_PENDING_CRASH).apply()
    }

    // Send bug report or crash report direct to GitHub
    suspend fun sendReportToGithub(
        context: Context,
        owner: String,
        repo: String,
        token: String,
        throwable: Throwable?,
        isFatal: Boolean,
        contextInfo: String,
        customJsonStr: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val realToken = token.trim()
            if (realToken.isEmpty()) {
                return@withContext Result.failure(Exception("GitHub Token is empty."))
            }

            val jsonToSubmit = if (customJsonStr != null) {
                JSONObject(customJsonStr)
            } else {
                if (throwable == null) {
                    return@withContext Result.failure(Exception("No exception/throwable or JSON is provided."))
                }
                constructCrashJson(context, throwable, isFatal, contextInfo)
            }

            val exceptionClass = jsonToSubmit.optString("exception_class")
            val exceptionMessage = jsonToSubmit.optString("exception_message")
            val isFatalReport = jsonToSubmit.optBoolean("is_fatal")
            val contextReport = jsonToSubmit.optString("context_info")
            val appVersion = jsonToSubmit.optString("app_version")
            val stackTrace = jsonToSubmit.optString("stack_trace")
            
            val deviceBrand = jsonToSubmit.optString("brand")
            val deviceModel = jsonToSubmit.optString("model")
            val androidVersion = jsonToSubmit.optString("android_version")
            val sdkInt = jsonToSubmit.optInt("sdk_int")
            val fingerprint = jsonToSubmit.optString("fingerprint")
            val timestamp = jsonToSubmit.optLong("timestamp")
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

            val typeLabel = if (isFatalReport) "Fatal Crash" else "Handled Bug/Error"

            val issueTitle = "[$typeLabel] $exceptionClass: $exceptionMessage"

            val issueBody = """
                ## App Error / Bug Report ($typeLabel)
                
                **Timestamp:** $dateStr
                **App Version:** v$appVersion
                **Device:** $deviceBrand $deviceModel (Android $androidVersion, API $sdkInt)
                **Build Fingerprint:** `$fingerprint`
                
                ### Context / Action Info:
                ${if (contextReport.isNotBlank()) contextReport else "*No context specified*"}
                
                ### Exception Details:
                - **Class:** `$exceptionClass`
                - **Message:** `$exceptionMessage`
                
                ### Stack Trace:
                ```
                $stackTrace
                ```
                
                ---
                *Reported dynamically by Overwork Journal Crash Reporter.*
            """.trimIndent()

            val requestBodyJson = JSONObject().apply {
                put("title", issueTitle)
                put("body", issueBody)
                put("labels", JSONArray(listOf("bug", if (isFatalReport) "crash" else "error")))
            }

            val url = "https://api.github.com/repos/$owner/$repo/issues"
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("Authorization", "Bearer $realToken")
                .addHeader("User-Agent", "OverworkJournal-App")
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respStr = response.body?.string() ?: ""
                    val respJson = JSONObject(respStr)
                    val issueUrl = respJson.optString("html_url")
                    Result.success(issueUrl)
                } else {
                    val errBody = response.body?.string() ?: "No error body"
                    Result.failure(Exception("GitHub API error. Code: ${response.code}, message: $errBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun constructCrashJson(context: Context, throwable: Throwable, isFatal: Boolean, contextInfo: String): JSONObject {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        return JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("exception_class", throwable.javaClass.name)
            put("exception_message", throwable.message ?: "No message")
            put("stack_trace", stackTrace)
            put("is_fatal", isFatal)
            put("context_info", contextInfo)
            put("app_version", getAppVersion(context))
            put("android_version", Build.VERSION.RELEASE)
            put("sdk_int", Build.VERSION.SDK_INT)
            put("brand", Build.BRAND)
            put("model", Build.MODEL)
            put("fingerprint", Build.FINGERPRINT)
        }
    }

    private fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}
