package com.revanced.net.revancedmanager.core.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.File
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
     * Header logging is a debug aid; release builds have nothing to gain from it.
     * Read off the manifest's debuggable flag rather than BuildConfig, which this module is not
     * set up to generate.
     */
    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(
        @ApplicationContext context: Context
    ): HttpLoggingInterceptor {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return HttpLoggingInterceptor().apply {
            level = if (debuggable) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    @Provides
    @Singleton
    @ApiClient
    fun provideApiOkHttpClient(
        @ApplicationContext context: Context,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
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