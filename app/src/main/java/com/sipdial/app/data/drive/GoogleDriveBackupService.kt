package com.sipdial.app.data.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class DriveBackupFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedTime: String,
    val description: String,
    val isAppData: Boolean
)

object GoogleDriveBackupService {
    private const val FOLDER_NAME = "SIP Dial Backups"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"
    private const val BACKUP_MIME = "application/octet-stream"

    private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
    private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
    private const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"

    private const val DRIVE_ABOUT_URL = "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetchAccountEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        val aboutReq = Request.Builder()
            .url(DRIVE_ABOUT_URL)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        try {
            client.newCall(aboutReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val email = JSONObject(body).optJSONObject("user")?.optString("emailAddress").orEmpty()
                    if (email.isNotBlank()) return@withContext email
                }
            }
        } catch (_: Exception) {
        }

        val req = Request.Builder()
            .url(USERINFO_URL)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string().orEmpty()
                JSONObject(body).optString("email").takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun uploadBackup(
        accessToken: String,
        filename: String,
        bytes: ByteArray,
        useAppDataFolder: Boolean,
        description: String
    ): DriveBackupFile = withContext(Dispatchers.IO) {
        val parentId = if (useAppDataFolder) "appDataFolder" else ensureBackupFolder(accessToken)
        val metadataJson = buildUploadMetadataJson(
            filename = filename,
            parentId = parentId,
            description = description
        )

        val multipart = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addPart(bytes.toRequestBody(BACKUP_MIME.toMediaType()))
            .build()

        val url = DRIVE_UPLOAD_URL.toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id,name,size,modifiedTime,description,spaces")
            .build()

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .post(multipart)
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException(extractDriveError(resp.code, body))
            }
            parseSingleFileJson(body)
        }
    }

    suspend fun listBackups(accessToken: String): List<DriveBackupFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DriveBackupFile>()
        var firstError: IOException? = null
        var succeededSpaces = 0

        // Query both Drive visible folder backups and private appDataFolder backups
        for (space in listOf("drive", "appDataFolder")) {
            val url = DRIVE_FILES_URL.toHttpUrl().newBuilder()
                .addQueryParameter("spaces", space)
                .addQueryParameter("q", "name contains '.sipbak' and trashed = false")
                .addQueryParameter("orderBy", "modifiedTime desc")
                .addQueryParameter("pageSize", "30")
                .addQueryParameter("fields", "files(id,name,size,modifiedTime,description,spaces)")
                .build()

            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val err = IOException(extractDriveError(resp.code, body))
                    if (resp.code == 401) throw err
                    if (firstError == null) firstError = err
                } else {
                    succeededSpaces++
                    results += parseFileListJson(body, defaultAppData = space == "appDataFolder")
                }
            }
        }

        if (succeededSpaces == 0 && firstError != null) {
            throw firstError!!
        }

        results.distinctBy { it.id }.sortedByDescending { it.modifiedTime }
    }

    suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val url = "$DRIVE_FILES_URL/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media")
            .build()

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException(extractDriveError(resp.code, resp.body?.string().orEmpty()))
            }
            resp.body?.bytes() ?: throw IOException("Empty backup response from Google Drive")
        }
    }

    suspend fun deleteBackup(accessToken: String, fileId: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$DRIVE_FILES_URL/$fileId")
            .header("Authorization", "Bearer $accessToken")
            .delete()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 404) {
                throw IOException(extractDriveError(resp.code, resp.body?.string().orEmpty()))
            }
        }
    }

    private fun ensureBackupFolder(accessToken: String): String {
        val searchUrl = DRIVE_FILES_URL.toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("q", "name = '$FOLDER_NAME' and mimeType = '$FOLDER_MIME' and trashed = false")
            .addQueryParameter("pageSize", "1")
            .addQueryParameter("fields", "files(id,name)")
            .build()

        val searchReq = Request.Builder()
            .url(searchUrl)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(searchReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException(extractDriveError(resp.code, body))
            }
            val arr = JSONObject(body).optJSONArray("files")
            if (arr != null && arr.length() > 0) {
                val existingId = arr.getJSONObject(0).optString("id")
                if (existingId.isNotBlank()) return existingId
            }
        }

        val createMeta = JSONObject()
            .put("name", FOLDER_NAME)
            .put("mimeType", FOLDER_MIME)
            .toString()

        val createReq = Request.Builder()
            .url(DRIVE_FILES_URL)
            .header("Authorization", "Bearer $accessToken")
            .post(createMeta.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(createReq).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException(extractDriveError(resp.code, body))
            }
            val id = JSONObject(body).optString("id")
            if (id.isBlank()) throw IOException("Failed to create SIP Dial Backups folder in Google Drive")
            return id
        }
    }

    internal fun buildUploadMetadataJson(
        filename: String,
        parentId: String,
        description: String
    ): String = JSONObject()
        .put("name", filename)
        .put("mimeType", BACKUP_MIME)
        .put("description", description)
        .put("parents", JSONArray().put(parentId))
        .toString()

    internal fun parseFileListJson(json: String, defaultAppData: Boolean = false): List<DriveBackupFile> {
        val arr = JSONObject(json).optJSONArray("files") ?: return emptyList()
        val list = ArrayList<DriveBackupFile>(arr.length())
        for (i in 0 until arr.length()) {
            list += parseFileObject(arr.getJSONObject(i), defaultAppData)
        }
        return list
    }

    internal fun parseSingleFileJson(json: String, defaultAppData: Boolean = false): DriveBackupFile =
        parseFileObject(JSONObject(json), defaultAppData)

    private fun parseFileObject(obj: JSONObject, defaultAppData: Boolean): DriveBackupFile {
        val spacesArr = obj.optJSONArray("spaces")
        var inAppData = defaultAppData
        if (spacesArr != null) {
            for (i in 0 until spacesArr.length()) {
                if (spacesArr.optString(i) == "appDataFolder") {
                    inAppData = true
                    break
                }
            }
        }
        return DriveBackupFile(
            id = obj.optString("id"),
            name = obj.optString("name"),
            sizeBytes = obj.optString("size", "0").toLongOrNull() ?: 0L,
            modifiedTime = obj.optString("modifiedTime"),
            description = obj.optString("description"),
            isAppData = inAppData
        )
    }

    private fun extractDriveError(code: Int, body: String): String {
        return try {
            val msg = JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
            if (msg.isNotBlank()) "Google Drive error ($code): $msg" else "Google Drive HTTP $code"
        } catch (_: Exception) {
            "Google Drive HTTP $code"
        }
    }
}
