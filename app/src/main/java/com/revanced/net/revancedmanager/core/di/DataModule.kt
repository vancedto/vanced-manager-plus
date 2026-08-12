package com.revanced.net.revancedmanager.core.di

import android.content.Context
import androidx.work.WorkManager
import com.revanced.net.revancedmanager.core.common.StringProvider
import com.revanced.net.revancedmanager.core.common.StringProviderImpl
import com.revanced.net.revancedmanager.data.remote.api.RevancedApiService
import com.revanced.net.revancedmanager.data.repository.AppRepositoryImpl
import com.revanced.net.revancedmanager.domain.repository.AppRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Hilt module for providing concrete dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideRevancedApiService(retrofit: Retrofit): RevancedApiService {
        return retrofit.create(RevancedApiService::class.java)
    }
}

/**
 * Hilt module for binding abstract dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingModule {

    @Binds
    @Singleton
    abstract fun bindAppRepository(
        appRepositoryImpl: AppRepositoryImpl
    ): AppRepository

    @Binds
    @Singleton
    abstract fun bindStringProvider(
        stringProviderImpl: StringProviderImpl
    ): StringProvider
} 