package com.matrixmessenger.di

import android.content.Context
import com.matrixmessenger.core.webrtc.WebRtcManager
import com.matrixmessenger.feature.call.data.repository.CallRepositoryImpl
import com.matrixmessenger.feature.call.domain.repository.CallRepository
import com.matrixmessenger.feature.call.domain.usecase.*
import com.matrixmessenger.feature.search.data.repository.SearchRepositoryImpl
import com.matrixmessenger.feature.search.domain.repository.SearchRepository
import com.matrixmessenger.feature.search.domain.usecase.SearchMessagesInRoomUseCase
import com.matrixmessenger.feature.search.domain.usecase.SearchUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
abstract class FeatureBindingsModule {

    // Call Feature Bindings
    @Binds
    abstract fun bindCallRepository(
        callRepositoryImpl: CallRepositoryImpl
    ): CallRepository

    // Search Feature Bindings
    @Binds
    abstract fun bindSearchRepository(
        searchRepositoryImpl: SearchRepositoryImpl
    ): SearchRepository
}

@Module
@InstallIn(ViewModelComponent::class)
object FeatureProvidesModule {

    @Provides
    @ViewModelScoped
    fun provideWebRtcManager(
        @ApplicationContext context: Context
    ): WebRtcManager {
        return WebRtcManager(context).apply {
            initialize()
        }
    }

    @Provides
    @ViewModelScoped
    fun provideStartCallUseCase(
        repository: CallRepository
    ): StartCallUseCase {
        return StartCallUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideAnswerCallUseCase(
        repository: CallRepository
    ): AnswerCallUseCase {
        return AnswerCallUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideRejectCallUseCase(
        repository: CallRepository
    ): RejectCallUseCase {
        return RejectCallUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideEndCallUseCase(
        repository: CallRepository
    ): EndCallUseCase {
        return EndCallUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideToggleMicrophoneUseCase(
        repository: CallRepository
    ): ToggleMicrophoneUseCase {
        return ToggleMicrophoneUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideToggleCameraUseCase(
        repository: CallRepository
    ): ToggleCameraUseCase {
        return ToggleCameraUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideSwitchCameraUseCase(
        repository: CallRepository
    ): SwitchCameraUseCase {
        return SwitchCameraUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideSearchUseCase(
        repository: SearchRepository
    ): SearchUseCase {
        return SearchUseCase(repository)
    }

    @Provides
    @ViewModelScoped
    fun provideSearchMessagesInRoomUseCase(
        repository: SearchRepository
    ): SearchMessagesInRoomUseCase {
        return SearchMessagesInRoomUseCase(repository)
    }
}
