package io.github.c1921.namingdict.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavRepositoryTest {

    @Test
    fun uploadFavorites_successBranch() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(201))
            val repository = WebDavRepository(client = OkHttpClient())
            val config = buildConfig(server)

            val result = repository.uploadFavorites(
                config = config,
                payload = FavoritesSyncPayload(updatedAt = 1_700_000_000_000, favoriteOrder = listOf(3, 1))
            )

            assertTrue(result.success)
            assertEquals("上传成功", result.message)

            val mkcolRequest = server.takeRequest()
            assertEquals("MKCOL", mkcolRequest.method)
            val uploadRequest = server.takeRequest()
            assertEquals("PUT", uploadRequest.method)
        }
    }

    @Test
    fun uploadFavorites_httpFailureBranch() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(500))
            val repository = WebDavRepository(client = OkHttpClient())
            val config = buildConfig(server)

            val result = repository.uploadFavorites(
                config = config,
                payload = FavoritesSyncPayload(updatedAt = 1_700_000_000_000, favoriteOrder = listOf(1))
            )

            assertFalse(result.success)
            assertTrue(result.message.contains("上传失败"))
            assertTrue(result.message.contains("HTTP 500"))
        }
    }

    @Test
    fun downloadFavorites_non2xxBranch() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            val repository = WebDavRepository(client = OkHttpClient())
            val config = buildConfig(server)

            val result = repository.downloadFavorites(config)

            assertTrue(result.isFailure)
            assertNotNull(result.exceptionOrNull())
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("下载失败"))
        }
    }

    @Test
    fun downloadFavorites_emptyContentBranch() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody(""))
            val repository = WebDavRepository(client = OkHttpClient())
            val config = buildConfig(server)

            val result = repository.downloadFavorites(config)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("空内容"))
        }
    }

    @Test
    fun downloadFavorites_validPayloadBranch() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"version":1,"updatedAt":1700000000000,"favoriteOrder":[4,2,1]}""")
            )
            val repository = WebDavRepository(client = OkHttpClient())
            val config = buildConfig(server)

            val result = repository.downloadFavorites(config)

            assertTrue(result.isSuccess)
            val payload = result.getOrNull()
            assertNotNull(payload)
            assertEquals(listOf(4, 2, 1), payload?.favoriteOrder)
        }
    }

    private fun buildConfig(server: MockWebServer): WebDavConfig {
        return WebDavConfig(
            serverUrl = server.url("/webdav").toString().removeSuffix("/"),
            username = "test-user",
            password = "test-pass"
        )
    }
}
