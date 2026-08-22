package com.matrixmessenger.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.matrix.android.sdk.api.Matrix
import org.matrix.android.sdk.api.MatrixConfiguration
import org.matrix.android.sdk.api.auth.AuthenticationService
import org.matrix.android.sdk.api.provider.RoomDisplayNameFallbackProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MatrixModule {

    @Provides
    @Singleton
    fun provideRoomDisplayNameFallbackProvider(): RoomDisplayNameFallbackProvider {
        return object : RoomDisplayNameFallbackProvider {
            override fun excludedUserIds(roomId: String): List<String> = emptyList()
            override fun getNameFor1member(name: String): String = name
            override fun getNameFor2members(name1: String, name2: String): String = "$name1 and $name2"
            override fun getNameFor3members(name1: String, name2: String, name3: String): String = "$name1, $name2 and $name3"
            override fun getNameFor4members(name1: String, name2: String, name3: String, name4: String): String = "$name1, $name2, $name3 and $name4"
            override fun getNameFor4membersAndMore(name1: String, name2: String, name3: String, remainingCount: Int): String = "$name1, $name2, $name3 and $remainingCount others"
            override fun getNameForEmptyRoom(isDirect: Boolean, leftMemberNames: List<String>): String = "Empty room"
            override fun getNameForRoomInvite(): String = "Room invite"
        }
    }

    @Provides
    @Singleton
    fun provideMatrixConfiguration(
        roomDisplayNameFallbackProvider: RoomDisplayNameFallbackProvider
    ): MatrixConfiguration {
        return MatrixConfiguration(
            applicationFlavor = "MatrixMessenger",
            roomDisplayNameFallbackProvider = roomDisplayNameFallbackProvider
        )
    }

    @Provides
    @Singleton
    fun provideMatrix(
        @ApplicationContext context: Context,
        matrixConfiguration: MatrixConfiguration
    ): Matrix {
        return Matrix(context, matrixConfiguration)
    }

    @Provides
    @Singleton
    fun provideAuthenticationService(matrix: Matrix): AuthenticationService {
        return matrix.authenticationService()
    }
}
