package com.example.base.core.domain.model

/**
 * 领域实体。刻意不带任何序列化注解——DTO 在 `:core:network`，映射在 `:core:data`。
 * 这样接口字段改名不会渗透到上层。
 */
data class Article(
    val id: String,
    val title: String,
    val summary: String,
    val author: String,
)
