package com.matrixmessenger.core.di

import com.matrixmessenger.core.coroutine.DefaultDispatcherProvider
import com.matrixmessenger.core.coroutine.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider {
        return DefaultDispatcherProvider()
    }
}
