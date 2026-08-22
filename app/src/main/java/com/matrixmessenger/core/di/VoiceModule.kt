package com.matrixmessenger.core.di

import android.content.Context
import com.matrixmessenger.feature.voice.data.recorder.AudioRecorder
import com.matrixmessenger.feature.voice.data.recorder.VideoNoteRecorder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {

    @Provides
    @Singleton
    fun provideAudioRecorder(@ApplicationContext context: Context): AudioRecorder {
        return AudioRecorder(context)
    }

    @Provides
    @Singleton
    fun provideVideoNoteRecorder(@ApplicationContext context: Context): VideoNoteRecorder {
        return VideoNoteRecorder(context)
    }
}
