package com.example.base.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 对应 jsonplaceholder.typicode.com/posts 的一条记录。
 *
 * 字段名跟着接口走，不迁就领域模型。缺字段就让反序列化失败——静默补默认值会把
 * 契约不一致藏起来，只有 `userId` 给默认值，因为它对展示不是必需的。
 */
@Serializable
data class ArticleDto(
    val id: Int,
    val title: String,
    val body: String,
    @SerialName("userId") val userId: Int = 0,
)
