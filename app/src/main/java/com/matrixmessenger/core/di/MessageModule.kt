package com.matrixmessenger.core.di

import com.matrixmessenger.feature.message.presentation.renderer.MessageRenderer
import com.matrixmessenger.feature.message.presentation.renderer.MessageRendererImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MessageModule {

    @Binds
    @Singleton
    abstract fun bindMessageRenderer(
        messageRendererImpl: MessageRendererImpl
    ): MessageRenderer
}
