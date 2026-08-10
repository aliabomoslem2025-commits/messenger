package com.matrixmessenger.di

import android.content.Context
import androidx.work.Configuration
import com.matrixmessenger.data.local.AppPreferences
import com.matrixmessenger.data.repository.MatrixRepositoryImpl
import com.matrixmessenger.domain.repository.MatrixRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindMatrixRepository(
        matrixRepositoryImpl: MatrixRepositoryImpl
    ): MatrixRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideAppPreferences(
        @ApplicationContext context: Context
    ): AppPreferences {
        return AppPreferences(context)
    }
}
