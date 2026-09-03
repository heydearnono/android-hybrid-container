package com.heydearnono.hybrid.core.domain.usecase

import com.heydearnono.hybrid.core.common.AppError
import com.heydearnono.hybrid.core.common.Outcome
import com.heydearnono.hybrid.core.domain.model.Article
import com.heydearnono.hybrid.core.domain.repository.ArticleRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 手写 fake，不用 mock 框架：接口改了这个类会编译不过，错误直接指到这里。
 */
private class FakeArticleRepository(
    private val result: Outcome<List<Article>>,
) : ArticleRepository {
    override suspend fun articles(): Outcome<List<Article>> = result
}

private fun article(
    id: String,
    title: String,
) = Article(
    id = id,
    title = title,
    summary = "summary $id",
    author = "author $id",
)

class GetArticlesUseCaseTest {
    @Test
    fun `按标题忽略大小写排序`() =
        runTest {
            val useCase =
                GetArticlesUseCase(
                    FakeArticleRepository(
                        Outcome.Success(
                            listOf(article("1", "banana"), article("2", "Apple"), article("3", "cherry")),
                        ),
                    ),
                )

            val result = useCase()

            assertEquals(
                listOf("Apple", "banana", "cherry"),
                (result as Outcome.Success).value.map { it.title },
            )
        }

    @Test
    fun `丢掉标题为空白的条目`() =
        runTest {
            val useCase =
                GetArticlesUseCase(
                    FakeArticleRepository(
                        Outcome.Success(listOf(article("1", "ok"), article("2", "   "))),
                    ),
                )

            val result = useCase() as Outcome.Success

            assertEquals(listOf("1"), result.value.map { it.id })
        }

    @Test
    fun `失败原样透传，不吞掉错误`() =
        runTest {
            val useCase = GetArticlesUseCase(FakeArticleRepository(Outcome.Failure(AppError.Network)))

            assertEquals(Outcome.Failure(AppError.Network), useCase())
        }
}
