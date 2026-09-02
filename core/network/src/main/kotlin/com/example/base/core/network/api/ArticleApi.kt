package com.example.base.core.network.api

import com.example.base.core.network.dto.ArticleDto
import retrofit2.http.GET

interface ArticleApi {
    /** jsonplaceholder 的公共接口，无需鉴权，适合做联通性样例。 */
    @GET("posts")
    suspend fun articles(): List<ArticleDto>
}
