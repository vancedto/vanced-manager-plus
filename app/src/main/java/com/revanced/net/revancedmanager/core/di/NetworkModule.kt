package com.revanced.net.revancedmanager.core.di

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.revanced.net.revancedmanager.data.manager.redactUrl
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import retrofit2.Retrofit
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt module for providing network-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class ApiClient

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class DownloadClient

    private const val HTTP_CACHE_DIR = "http_cache"
    private const val HTTP_CACHE_SIZE_BYTES = 20L * 1024 * 1024

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /**
     * One line per request for the debug log: method, host+path, status and time. Query strings
     * are dropped via [redactUrl] and headers/bodies are never logged, so it is safe in release
     * builds, where a user with debug mode on is who needs it.
     */
    @Provides
    @Singleton
    fun provideRequestLogInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val target = redactUrl(request.url.toString())
        val start = SystemClock.elapsedRealtime()
        try {
            val response = chain.proceed(request)
            Log.i(
                "Http",
                "${request.method} $target -> ${response.code} " +
                    "(${SystemClock.elapsedRealtime() - start} ms${if (response.cacheResponse != null) ", cached" else ""})"
            )
            response
        } catch (e: IOException) {
            Log.w("Http", "${request.method} $target failed after ${SystemClock.elapsedRealtime() - start} ms: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    @Provides
    @Singleton
    @ApiClient
    fun provideApiOkHttpClient(
        @ApplicationContext context: Context,
        requestLogInterceptor: Interceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(requestLogInterceptor)
            // The v3 catalog is one document of about a megabyte at ~500 apps, refetched on every
            // refresh. Caddy serves it with an ETag, so with a cache here each refresh after the
            // first is a ~200 byte conditional request and OkHttp replays the body from disk.
            .cache(Cache(File(context.cacheDir, HTTP_CACHE_DIR), HTTP_CACHE_SIZE_BYTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @DownloadClient
    fun provideDownloadOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        @ApiClient okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://vanced.to/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
} 