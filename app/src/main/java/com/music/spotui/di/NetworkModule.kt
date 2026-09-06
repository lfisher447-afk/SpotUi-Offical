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
import okhttp3.OkHttpClient

/** Configures network clients used by provider metadata and lyrics services. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    /** Provides a conservative OkHttp client with a descriptive user agent. */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Constants.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(Constants.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(Constants.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "SpotUI/${BuildConfig.VERSION_NAME}")
                .build()
            chain.proceed(request)
        }
        .build()

    /** Provides the Ktor client used by suspend remote-service calls. */
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }
}
