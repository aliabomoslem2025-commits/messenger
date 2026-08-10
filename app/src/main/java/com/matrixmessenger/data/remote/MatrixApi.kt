package com.matrixmessenger.data.remote

import com.matrixmessenger.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatrixApi @Inject constructor() {
    
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Timber.tag("MatrixAPI").d(message)
    }.apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.BASIC
        }
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    fun createApiClient(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    private fun String.ensureTrailingSlash(): String {
        return if (endsWith("/")) this else "$this/"
    }
}

interface MatrixAuthService {
    // Authentication endpoints will be implemented using the Matrix SDK directly
    // This interface is kept for any custom auth operations if needed
}

interface MatrixRoomService {
    // Room-related endpoints will be implemented using the Matrix SDK directly
}

interface MatrixMessageService {
    // Message-related endpoints will be implemented using the Matrix SDK directly
}
