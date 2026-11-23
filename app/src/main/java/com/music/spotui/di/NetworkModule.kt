// File: app/src/main/java/com/music/spotui/di/NetworkModule.kt
package com.music.spotui.di

import com.music.spotui.BuildConfig
import com.music.spotui.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol

/** Configures high-concurrency HTTP/2 network clients used by provider metadata and lyrics services. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 16
            },
        )
        .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectTimeout(Constants.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(Constants.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(Constants.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "SpotUI/${BuildConfig.VERSION_NAME}")
                .build()
            chain.proceed(request)
        }
        .build()

    @Provides
    @Singleton
    fun provideHttpClient(okHttpClient: OkHttpClient): HttpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }
}
