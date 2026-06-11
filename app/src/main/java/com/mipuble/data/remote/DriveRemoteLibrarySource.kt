package com.mipuble.data.remote

import com.mipuble.domain.model.RemoteBook
import java.io.File
import javax.inject.Inject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * The real Google Drive source (Drive REST API v3). It lists EPUBs in the
 * user's Drive and streams a chosen file on demand. Authentication is delegated
 * to [DriveAuthProvider]; without a token every call reports "unavailable", so
 * this can ship inert and be activated by wiring a real provider.
 *
 * Not bound by default (the app uses [FakeRemoteLibrarySource] so it runs
 * offline); swap the binding in RemoteModule once OAuth is configured.
 */
class DriveRemoteLibrarySource @Inject constructor(
    private val client: OkHttpClient,
    private val authProvider: DriveAuthProvider,
) : RemoteLibrarySource {

    override suspend fun isAvailable(): Boolean = authProvider.accessToken() != null

    override suspend fun listBooks(): List<RemoteBook> {
        val token = authProvider.accessToken() ?: return emptyList()
        val url = "$BASE/files?q=${"mimeType='application/epub+zip' and trashed=false".encode()}" +
            "&fields=files(id,name,size)&pageSize=200"
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Drive list failed: ${response.code}")
            return parseDriveFiles(body)
        }
    }

    override suspend fun fetchCover(remoteId: String): ByteArray? {
        // Drive can return a thumbnailLink; omitted here to avoid a second
        // round-trip — covers are extracted from the EPUB after download.
        return null
    }

    override suspend fun download(remoteId: String, target: File, onProgress: (Float) -> Unit) {
        val token = authProvider.accessToken() ?: error("Not signed in to Drive")
        val request = Request.Builder()
            .url("$BASE/files/$remoteId?alt=media")
            .header("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Drive download failed: ${response.code}")
            val body = response.body ?: error("Empty response")
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
                        if (total != null) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            onProgress(1f)
        }
    }

    private companion object {
        const val BASE = "https://www.googleapis.com/drive/v3"
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
