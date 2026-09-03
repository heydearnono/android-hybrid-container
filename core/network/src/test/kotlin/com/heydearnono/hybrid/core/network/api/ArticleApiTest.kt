package com.heydearnono.hybrid.core.network.api

import com.heydearnono.hybrid.core.network.defaultJson
import com.heydearnono.hybrid.core.network.okHttpClient
import com.heydearnono.hybrid.core.network.retrofit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import retrofit2.HttpException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * MockWebServer 是纯 JVM 的，所以序列化和状态码分支能在没有设备的环境里真实跑通。
 */
class ArticleApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ArticleApi

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        api =
            retrofit(
                baseUrl = server.url("/").toString(),
                client = okHttpClient(loggingEnabled = false),
                json = defaultJson(),
            ).create(ArticleApi::class.java)
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun `正常响应能解析，多余字段被忽略`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body(
                        """
                        [{"userId":7,"id":1,"title":"t","body":"b","unexpected":"x"}]
                        """.trimIndent(),
                    ).build(),
            )

            val result = api.articles()

            assertEquals(1, result.size)
            assertEquals(7, result.first().userId)
            assertEquals("t", result.first().title)
        }

    @Test
    fun `请求打到了 posts 路径`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body("[]")
                    .build(),
            )

            api.articles()

            assertEquals("/posts", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `5xx 抛 HttpException 并带上状态码`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(500)
                    .body("")
                    .build(),
            )

            val error = assertFailsWith<HttpException> { api.articles() }

            assertEquals(500, error.code())
        }

    @Test
    fun `缺必填字段时反序列化失败，不静默补默认值`() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body("""[{"id":1,"title":"t"}]""")
                    .build(),
            )

            assertFailsWith<SerializationException> { api.articles() }
        }
}
