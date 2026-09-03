package com.heydearnono.hybrid.feature.articles

import com.heydearnono.hybrid.core.common.AppError
import com.heydearnono.hybrid.core.common.Outcome
import com.heydearnono.hybrid.core.domain.model.Article
import com.heydearnono.hybrid.core.domain.repository.ArticleRepository
import com.heydearnono.hybrid.core.domain.usecase.GetArticlesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class FakeArticleRepository : ArticleRepository {
    var result: Outcome<List<Article>> = Outcome.Success(emptyList())
    var callCount = 0
        private set

    override suspend fun articles(): Outcome<List<Article>> {
        callCount++
        return result
    }
}

/**
 * ViewModel 的测试跑在 JVM 上（`testDebugUnitTest`），不需要设备。
 * viewModelScope 用 Dispatchers.Main，所以必须先 setMain。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArticlesViewModelTest {
    private val repository = FakeArticleRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ArticlesViewModel(GetArticlesUseCase(repository))

    @Test
    fun `有数据时进入 Content`() =
        runTest {
            repository.result =
                Outcome.Success(
                    listOf(Article(id = "1", title = "t", summary = "s", author = "a")),
                )

            val state = viewModel().state.value

            assertEquals(listOf("t"), assertIs<ArticlesUiState.Content>(state).articles.map { it.title })
        }

    @Test
    fun `空列表进入 Empty，而不是显示一个空的 Content`() =
        runTest {
            repository.result = Outcome.Success(emptyList())

            assertIs<ArticlesUiState.Empty>(viewModel().state.value)
        }

    @Test
    fun `失败进入 Error 并带上错误类型`() =
        runTest {
            repository.result = Outcome.Failure(AppError.Http(503))

            val state = assertIs<ArticlesUiState.Error>(viewModel().state.value)

            assertEquals(AppError.Http(503), state.error)
        }

    @Test
    fun `refresh 会重新请求并从 Error 恢复`() =
        runTest {
            repository.result = Outcome.Failure(AppError.Network)
            val viewModel = viewModel()
            assertIs<ArticlesUiState.Error>(viewModel.state.value)

            repository.result =
                Outcome.Success(
                    listOf(Article(id = "1", title = "t", summary = "s", author = "a")),
                )
            viewModel.refresh()

            assertIs<ArticlesUiState.Content>(viewModel.state.value)
            assertEquals(2, repository.callCount)
        }
}
