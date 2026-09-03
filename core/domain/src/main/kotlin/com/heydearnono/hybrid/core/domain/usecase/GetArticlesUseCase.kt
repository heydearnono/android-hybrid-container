package com.heydearnono.hybrid.core.domain.usecase

import com.heydearnono.hybrid.core.common.Outcome
import com.heydearnono.hybrid.core.common.map
import com.heydearnono.hybrid.core.domain.model.Article
import com.heydearnono.hybrid.core.domain.repository.ArticleRepository

/**
 * 取文章列表，丢掉标题为空的脏数据，按标题排序。
 *
 * 这类「展示前的整理」放在 use case 而不是 ViewModel：纯函数、纯 JVM，测试成本最低。
 */
class GetArticlesUseCase(
    private val repository: ArticleRepository,
) {
    suspend operator fun invoke(): Outcome<List<Article>> =
        repository.articles().map { articles ->
            articles
                .filter { it.title.isNotBlank() }
                .sortedBy { it.title.lowercase() }
        }
}
