package com.heydearnono.hybrid.core.domain.repository

import com.heydearnono.hybrid.core.common.Outcome
import com.heydearnono.hybrid.core.domain.model.Article

/**
 * 数据来源的抽象。实现在 `:core:data`，domain 只认这个接口。
 *
 * 返回 [Outcome] 而不是抛异常：失败是预期结果之一，调用方必须显式处理。
 */
interface ArticleRepository {
    suspend fun articles(): Outcome<List<Article>>
}
