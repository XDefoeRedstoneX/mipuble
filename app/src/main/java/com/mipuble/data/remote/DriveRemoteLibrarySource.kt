package com.mipuble.data.remote

import com.mipuble.domain.model.RemoteBook
import java.io.File
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject

/**
 * The real Google Drive source (Drive REST API v3). Everything is scoped to a
 * single app-owned folder named "mipuble": listing, downloading, and uploading
 * all target that folder, so the rest of the user's Drive is never touched.
 * Auth is delegated to [DriveAuthProvider]; without a token calls report
 * "unavailable" / throw, so this can ship inert until OAuth is wired.
 */
class DriveRemoteLibrarySource @Inject constructor(
    private val client: OkHttpClient,
    private val authProvider: DriveAuthProvider,
) : RemoteLibrarySource {

    @Volatile
    private var cachedFolderId: String? = null

    override suspend fun isAvailable(): Boolean = authProvider.authenticate() is AuthResult.Success

    override suspend fun listBooks(): List<RemoteBook> {
        val token = token() ?: return emptyList()
        val folderId = ensureFolderId(token)
        val q = "'$folderId' in parents and mimeType='application/epub+zip' and trashed=false"
        val url = "$BASE/files?q=${q.encode()}&fields=files(id,name,size)&pageSize=200"
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Drive list failed: ${response.code} ${body.take(200)}")
            return parseDriveFiles(body)
        }
    }

    override suspend fun fetchCover(remoteId: String): ByteArray? = null

    override suspend fun download(remoteId: String, target: File, onProgress: (read: Long, total: Long?) -> Unit) {
        val token = token() ?: error("Not signed in to Drive")
        val request = Request.Builder()
            .url("$BASE/files/$remoteId?alt=media")
            .header("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Drive download failed: ${response.code} ${response.body?.string()?.take(200).orEmpty()}")
            }
            val body = response.body ?: error("Empty response")
            // Content-Length is often absent (chunked/gzip), so total may be null.
            val total = body.contentLength().takeIf { it > 0 }
            var read = 0L
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                    output.flush()
                }
            }
        }
    }

    override suspend fun uploadBook(
        file: File,
        displayName: String,
        onProgress: (Float) -> Unit,
    ): RemoteBook {
        val token = token() ?: error("Not signed in to Drive")
        val folderId = ensureFolderId(token)
        val name = if (displayName.endsWith(".epub", ignoreCase = true)) displayName else "$displayName.epub"

        val metadata = JSONObject()
            .put("name", name)
            .put("parents", JSONArray().put(folderId))
            .toString()
        val media = ProgressRequestBody(file.asRequestBody(EPUB_MEDIA.toMediaType()), onProgress)
        val multipart = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(media)
            .build()

        val request = Request.Builder()
            .url("$UPLOAD/files?uploadType=multipart&fields=id,name,size")
            .header("Authorization", "Bearer $token")
            .post(multipart)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Drive upload failed: ${response.code} ${body.take(200)}")
            val obj = JSONObject(body)
            return RemoteBook(
                remoteId = obj.getString("id"),
                title = name.removeSuffix(".epub"),
                author = "",
                sizeBytes = obj.optString("size").toLongOrNull() ?: file.length(),
            )
        }
    }

    override suspend fun trashBook(remoteId: String) {
        val token = token() ?: error("Not signed in to Drive")
        // trashed=true moves it to Drive's trash (recoverable), not a hard delete.
        val body = JSONObject().put("trashed", true).toString()
            .toRequestBody("application/json; charset=UTF-8".toMediaType())
        val request = Request.Builder()
            .url("$BASE/files/$remoteId")
            .header("Authorization", "Bearer $token")
            .patch(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Drive trash failed: ${response.code}")
        }
    }

    /** Finds the app's "mipuble" folder, creating it if absent; caches the id. */
    private fun ensureFolderId(token: String): String {
        cachedFolderId?.let { return it }

        val q = "name='$FOLDER_NAME' and mimeType='$FOLDER_MIME' and trashed=false"
        val listUrl = "$BASE/files?q=${q.encode()}&fields=files(id)&pageSize=1"
        val found = client.newCall(
            Request.Builder().url(listUrl).header("Authorization", "Bearer $token").build(),
        ).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Drive folder lookup failed: ${response.code}")
            JSONObject(body).optJSONArray("files")?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotEmpty() }
        }
        if (found != null) return found.also { cachedFolderId = it }

        val createBody = JSONObject()
            .put("name", FOLDER_NAME)
            .put("mimeType", FOLDER_MIME)
            .toString()
            .toRequestBody("application/json; charset=UTF-8".toMediaType())
        return client.newCall(
            Request.Builder()
                .url("$BASE/files?fields=id")
                .header("Authorization", "Bearer $token")
                .post(createBody)
                .build(),
        ).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Drive folder create failed: ${response.code} ${body.take(200)}")
            JSONObject(body).getString("id")
        }.also { cachedFolderId = it }
    }

    private suspend fun token(): String? =
        (authProvider.authenticate() as? AuthResult.Success)?.token

    private companion object {
        const val BASE = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FOLDER_NAME = "mipuble"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val EPUB_MEDIA = "application/epub+zip"
    }
}

/** Wraps a [RequestBody] to report upload progress as bytes stream out. */
private class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        val counting = object : ForwardingSink(sink) {
            var written = 0L
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                written += byteCount
                if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
            }
        }
        val buffered = counting.buffer()
        delegate.writeTo(buffered)
        buffered.flush()
    }
}

/** Pure parser for a Drive files.list response — unit-tested without the network. */
internal fun parseDriveFiles(json: String): List<RemoteBook> {
    val files = JSONObject(json).optJSONArray("files") ?: return emptyList()
    return (0 until files.length()).mapNotNull { i ->
        val obj = files.optJSONObject(i) ?: return@mapNotNull null
        val id = obj.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val name = obj.optString("name").ifEmpty { "Untitled" }
        RemoteBook(
            remoteId = id,
            title = name.removeSuffix(".epub"),
            author = "",
            sizeBytes = obj.optString("size").toLongOrNull() ?: 0L,
        )
    }
}

private fun String.encode(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
