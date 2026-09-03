package com.heydearnono.hybrid.core.data.repository

import com.heydearnono.hybrid.core.common.AppError
import com.heydearnono.hybrid.core.common.DispatcherProvider
import com.heydearnono.hybrid.core.common.Outcome
import com.heydearnono.hybrid.core.data.mapper.toDomain
import com.heydearnono.hybrid.core.domain.model.Article
import com.heydearnono.hybrid.core.domain.repository.ArticleRepository
import com.heydearnono.hybrid.core.network.api.ArticleApi
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/**
 * 异常在这里被拦住并翻译成 [AppError]。
 *
 * 这是 domain 与 network 之间唯一的边界：往上不再有 `IOException` / `HttpException`。
 */
class DefaultArticleRepository(
    private val api: ArticleApi,
    private val dispatchers: DispatcherProvider,
) : ArticleRepository {
    override suspend fun articles(): Outcome<List<Article>> =
        withContext(dispatchers.io) {
            try {
                Outcome.Success(api.articles().map { it.toDomain() })
            } catch (e: HttpException) {
                Outcome.Failure(AppError.Http(e.code()))
            } catch (e: SerializationException) {
                Outcome.Failure(AppError.Serialization)
            } catch (e: IOException) {
                Outcome.Failure(AppError.Network)
            }
        }
}
