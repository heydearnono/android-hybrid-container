package com.heydearnono.hybrid.core.data.repository

import com.heydearnono.hybrid.core.common.AppError
import com.heydearnono.hybrid.core.common.DispatcherProvider
import com.heydearnono.hybrid.core.common.Outcome
import com.heydearnono.hybrid.core.network.api.ArticleApi
import com.heydearnono.hybrid.core.network.dto.ArticleDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

private class TestDispatchers(
    dispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}

private class FakeArticleApi(
    private val respond: () -> List<ArticleDto>,
) : ArticleApi {
    override suspend fun articles(): List<ArticleDto> = respond()
}

private fun httpException(code: Int) =
    HttpException(
        Response.error<List<ArticleDto>>(code, "".toResponseBody("application/json".toMediaType())),
    )

/**
 * 这层的职责只有两件：映射，以及把异常翻译成 [AppError]。四个用例分别锁住这两件事。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultArticleRepositoryTest {
    private fun repository(respond: () -> List<ArticleDto>) =
        DefaultArticleRepository(
            api = FakeArticleApi(respond),
            dispatchers = TestDispatchers(UnconfinedTestDispatcher()),
        )

    @Test
    fun `DTO 映射成领域实体`() =
        runTest {
            val repo =
                repository {
                    listOf(ArticleDto(id = 1, title = "  t  ", body = "  b  ", userId = 7))
                }

            val article = (repo.articles() as Outcome.Success).value.single()

            assertEquals("1", article.id)
            assertEquals("t", article.title)
            assertEquals("b", article.summary)
            assertEquals("user 7", article.author)
        }

    @Test
    fun `HttpException 翻译成 AppError_Http 并保留状态码`() =
        runTest {
            val repo = repository { throw httpException(404) }

            assertEquals(Outcome.Failure(AppError.Http(404)), repo.articles())
        }

    @Test
    fun `IOException 翻译成 AppError_Network`() =
        runTest {
            val repo = repository { throw IOException("boom") }

            assertEquals(Outcome.Failure(AppError.Network), repo.articles())
        }

    @Test
    fun `SerializationException 翻译成 AppError_Serialization`() =
        runTest {
            val repo = repository { throw SerializationException("bad") }

            assertEquals(Outcome.Failure(AppError.Serialization), repo.articles())
        }
}
