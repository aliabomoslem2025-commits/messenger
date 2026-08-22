package com.matrixmessenger.core.di

import com.matrixmessenger.data.repository.AuthRepositoryImpl
import com.matrixmessenger.data.repository.MatrixRepositoryImpl
import com.matrixmessenger.data.repository.MediaRepositoryImpl
import com.matrixmessenger.data.repository.MessageRepositoryImpl
import com.matrixmessenger.data.repository.RoomRepositoryImpl
import com.matrixmessenger.data.repository.UserRepositoryImpl
import com.matrixmessenger.domain.repository.AuthRepository
import com.matrixmessenger.domain.repository.MatrixRepository
import com.matrixmessenger.domain.repository.MediaRepository
import com.matrixmessenger.domain.repository.MessageRepository
import com.matrixmessenger.domain.repository.RoomRepository
import com.matrixmessenger.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
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

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindRoomRepository(
        roomRepositoryImpl: RoomRepositoryImpl
    ): RoomRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(
        messageRepositoryImpl: MessageRepositoryImpl
    ): MessageRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(
        mediaRepositoryImpl: MediaRepositoryImpl
    ): MediaRepository
}
