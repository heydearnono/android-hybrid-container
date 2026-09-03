package com.heydearnono.hybrid.core.data.mapper

import com.heydearnono.hybrid.core.domain.model.Article
import com.heydearnono.hybrid.core.network.dto.ArticleDto

/** DTO → 领域实体。映射只在这一层发生，两边的字段名各自独立演进。 */
internal fun ArticleDto.toDomain(): Article =
    Article(
        id = id.toString(),
        title = title.trim(),
        summary = body.trim(),
        author = "user $userId",
    )
