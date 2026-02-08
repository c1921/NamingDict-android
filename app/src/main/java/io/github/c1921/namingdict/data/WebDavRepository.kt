package io.github.c1921.namingdict.data

import io.github.c1921.namingdict.data.model.NamingScheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class FavoritesSyncPayload(
    val version: Int = 1,
    val updatedAt: Long = 0L,
    val favoriteOrder: List<Int> = emptyList()
)

@Serializable
data class NamePlansSyncPayload(
    val version: Int = 1,
    val updatedAt: Long = 0L,
    val surname: String = "",
    val schemes: List<NamingScheme> = emptyList()
)

data class WebDavConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = ""
) {
    fun isComplete(): Boolean {
        return serverUrl.isNotBlank() &&
            username.isNotBlank() &&
            password.isNotBlank()
    }

    fun isHttp(): Boolean = serverUrl.trim().startsWith("http://", ignoreCase = true)

    fun isHttps(): Boolean = serverUrl.trim().startsWith("https://", ignoreCase = true)

    fun buildFolderUrl(): String {
        val base = serverUrl.trim().removeSuffix("/")
        return "$base/$DEFAULT_FOLDER_NAME"
    }

    fun buildFileUrl(fileName: String = DEFAULT_FAVORITES_FILE_NAME): String {
        return "${buildFolderUrl()}/$fileName"
    }

    fun buildFavoritesFileUrl(): String {
        return buildFileUrl(DEFAULT_FAVORITES_FILE_NAME)
    }

    fun buildNamePlansFileUrl(): String {
        return buildFileUrl(DEFAULT_NAME_PLANS_FILE_NAME)
    }

    companion object {
        const val DEFAULT_FOLDER_NAME = "NamingDict"
        const val DEFAULT_FAVORITES_FILE_NAME = "favorites.json"
        const val DEFAULT_NAME_PLANS_FILE_NAME = "name_plans.json"
        const val DEFAULT_FILE_NAME = DEFAULT_FAVORITES_FILE_NAME
    }
}

data class SyncResult(
    val success: Boolean,
    val message: String
)

class WebDavRepository(
    private val client: OkHttpClient = OkHttpClient()
) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun uploadFavorites(config: WebDavConfig, payload: FavoritesSyncPayload): SyncResult {
        return uploadJsonFile(
            config = config,
            fileUrl = config.buildFavoritesFileUrl(),
            body = json.encodeToString(payload)
        )
    }

    suspend fun uploadNamePlans(config: WebDavConfig, payload: NamePlansSyncPayload): SyncResult {
        return uploadJsonFile(
            config = config,
            fileUrl = config.buildNamePlansFileUrl(),
            body = json.encodeToString(payload)
        )
    }

    suspend fun downloadFavorites(config: WebDavConfig): Result<FavoritesSyncPayload> {
        if (!config.isComplete()) {
            return Result.failure(IllegalStateException("WebDAV配置不完整，无法下载"))
        }
        val request = Request.Builder()
            .url(config.buildFavoritesFileUrl())
            .header("Authorization", Credentials.basic(config.username, config.password))
            .get()
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("下载失败：${httpStatusMessage(response.code)}")
                }
                val text = response.body?.string().orEmpty()
                if (text.isBlank()) {
                    error("下载失败：远端返回空内容")
                }
                json.decodeFromString<FavoritesSyncPayload>(text)
            }
        }
    }

    suspend fun downloadNamePlans(config: WebDavConfig): Result<NamePlansSyncPayload?> {
        if (!config.isComplete()) {
            return Result.failure(IllegalStateException("WebDAV配置不完整，无法下载"))
        }
        val request = Request.Builder()
            .url(config.buildNamePlansFileUrl())
            .header("Authorization", Credentials.basic(config.username, config.password))
            .get()
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    return@use null
                }
                if (!response.isSuccessful) {
                    error("下载起名方案失败：${httpStatusMessage(response.code)}")
                }
                val text = response.body?.string().orEmpty()
                if (text.isBlank()) {
                    error("下载起名方案失败：远端返回空内容")
                }
                json.decodeFromString<NamePlansSyncPayload>(text)
            }
        }
    }

    private fun uploadJsonFile(
        config: WebDavConfig,
        fileUrl: String,
        body: String
    ): SyncResult {
        if (!config.isComplete()) {
            return SyncResult(success = false, message = "WebDAV配置不完整，无法上传")
        }

        val folderResult = ensureFolderExists(config)
        if (!folderResult.success) {
            return folderResult
        }

        val request = Request.Builder()
            .url(fileUrl)
            .header("Authorization", Credentials.basic(config.username, config.password))
            .put(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    SyncResult(success = true, message = "上传成功")
                } else {
                    SyncResult(
                        success = false,
                        message = "上传失败：${httpStatusMessage(response.code)}"
                    )
                }
            }
        }.getOrElse { exception ->
            SyncResult(success = false, message = "上传失败：${exception.message ?: "网络异常"}")
        }
    }

    private fun ensureFolderExists(config: WebDavConfig): SyncResult {
        val request = Request.Builder()
            .url(config.buildFolderUrl())
            .header("Authorization", Credentials.basic(config.username, config.password))
            .method("MKCOL", null)
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 405) {
                    SyncResult(success = true, message = "目录已就绪")
                } else {
                    SyncResult(
                        success = false,
                        message = "创建目录失败：${httpStatusMessage(response.code)}"
                    )
                }
            }
        }.getOrElse { exception ->
            SyncResult(success = false, message = "创建目录失败：${exception.message ?: "网络异常"}")
        }
    }

    private fun httpStatusMessage(code: Int): String {
        return when (code) {
            401 -> "认证失败（401）"
            403 -> "无权限访问（403）"
            404 -> "远端文件不存在（404）"
            409 -> "目录不存在或路径冲突（409）"
            else -> "HTTP $code"
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
