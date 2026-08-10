package com.matrixmessenger.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.matrix.android.sdk.api.Matrix
import org.matrix.android.sdk.api.MatrixConfiguration
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.auth.AuthenticationService
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MatrixModule {

    @Provides
    @Singleton
    fun provideMatrixConfiguration(): MatrixConfiguration {
        return MatrixConfiguration(
            // Enable or disable auto encryption
            enableCryptoWhenStartingMatrix2 = true,
            // Enable or disable timeline decryption failure handling
            timelineDecryptionFailureHandler = MatrixConfiguration.TimelineDecryptionFailureHandler.Default(),
            // Configure logging
            userLogger = object : org.matrix.android.sdk.api.logger.Logger {
                override fun d(tag: String, message: String, throwable: Throwable?) {
                    Timber.tag(tag).d(message, throwable)
                }

                override fun i(tag: String, message: String, throwable: Throwable?) {
                    Timber.tag(tag).i(message, throwable)
                }

                override fun w(tag: String, message: String, throwable: Throwable?) {
                    Timber.tag(tag).w(message, throwable)
                }

                override fun e(tag: String, message: String, throwable: Throwable?) {
                    Timber.tag(tag).e(message, throwable)
                }
            }
        )
    }

    @Provides
    @Singleton
    fun provideMatrix(@ApplicationContext context: Context): Matrix {
        return Matrix.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideAuthenticationService(
        matrix: Matrix,
        matrixConfiguration: MatrixConfiguration
    ): AuthenticationService {
        return matrix.getAuthenticationService(matrixConfiguration)
    }
}
