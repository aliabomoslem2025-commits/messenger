package com.matrixmessenger.data.repository

import com.matrixmessenger.data.local.preferences.SessionPreferences
import com.matrixmessenger.data.matrix.MatrixClientManager
import com.matrixmessenger.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.matrix.android.sdk.api.auth.AuthenticationService
import org.matrix.android.sdk.api.auth.registration.RegistrationResult
import org.matrix.android.sdk.api.auth.registration.RegistrationWizard
import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.api.session.Session
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val matrixClientManager: MatrixClientManager,
    private val sessionPreferences: SessionPreferences,
    private val authenticationService: AuthenticationService
) : AuthRepository {

    override suspend fun login(username: String, password: String, homeserverUrl: String): Result<String> {
        return try {
            Timber.d("Attempting login for user: $username on homeserver: $homeserverUrl")
            
            // Get home server details
            val homeServerDetails = authenticationService.getHomeServerConnectionSpec(homeserverUrl)
            
            // Create login wizard
            val loginWizard = authenticationService.createLoginWizard(
                homeServerDetails
            )
            
            // Perform login
            val session = loginWizard
                .login(username.trim(), password)
                .perform()
            
            // Save session info
            sessionPreferences.saveSessionInfo(
                userId = session.myUserId,
                deviceId = session.sessionParams.deviceId ?: "",
                homeserverUrl = homeserverUrl,
                accessToken = (session.sessionParams as? org.matrix.android.sdk.api.auth.SessionParams.Full)?.accessToken ?: ""
            )
            
            Timber.d("Login successful for user: ${session.myUserId}")
            Result.success(session.myUserId)
        } catch (e: Exception) {
            Timber.e(e, "Login failed")
            Result.failure(mapMatrixError(e))
        }
    }

    override suspend fun register(
        username: String,
        password: String,
        homeserverUrl: String,
        email: String?
    ): Result<String> {
        return try {
            Timber.d("Attempting registration for user: $username on homeserver: $homeserverUrl")
            
            // Get home server details
            val homeServerDetails = authenticationService.getHomeServerConnectionSpec(homeserverUrl)
            
            // Create registration wizard
            val registrationWizard: RegistrationWizard = authenticationService
                .createRegistrationWizard(homeServerDetails)
                .beginRegistration()
            
            // Check if username is available
            registrationWizard.checkUsernameAvailability(username.trim())
            
            // Register with username and password
            val result: RegistrationResult = registrationWizard
                .registerWithPassword(username.trim(), password)
                .perform()
            
            when (result) {
                is RegistrationResult.Success -> {
                    val session = result.session
                    
                    // Save session info
                    sessionPreferences.saveSessionInfo(
                        userId = session.myUserId,
                        deviceId = session.sessionParams.deviceId ?: "",
                        homeserverUrl = homeserverUrl,
                        accessToken = (session.sessionParams as? org.matrix.android.sdk.api.auth.SessionParams.Full)?.accessToken ?: ""
                    )
                    
                    Timber.d("Registration successful for user: ${session.myUserId}")
                    Result.success(session.myUserId)
                }
                is RegistrationResult.Failure -> {
                    Result.failure(Exception("Registration failed: ${result.failureReason}"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Registration failed")
            Result.failure(mapMatrixError(e))
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            Timber.d("Logging out")
            
            // Logout from Matrix session
            matrixClientManager.getCurrentSession()?.let { session ->
                try {
                    session.logout()
                } catch (e: Exception) {
                    Timber.w(e, "Error during Matrix logout")
                }
            }
            
            // Clear saved session
            sessionPreferences.clearSession()
            
            // Clear active session
            matrixClientManager.logout()
            
            Timber.d("Logout successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Logout failed")
            Result.failure(e)
        }
    }

    override suspend fun getCurrentSessionUserId(): String? {
        return try {
            matrixClientManager.getCurrentUserId()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun isAuthenticated(): Boolean {
        return try {
            matrixClientManager.getCurrentSession() != null
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getActiveSession(): Any? {
        return matrixClientManager.getCurrentSession()
    }

    override fun observeAuthState(): Flow<Boolean> {
        return sessionPreferences.observeSessionInfo().map { sessionInfo ->
            sessionInfo?.userId?.isNotEmpty() == true
        }
    }

    /**
     * Map Matrix SDK errors to user-friendly messages
     */
    private fun mapMatrixError(exception: Exception): Exception {
        return when (exception) {
            is Failure.ApiException -> {
                when (exception.httpStatusCode) {
                    400 -> Exception("Invalid request")
                    401 -> Exception("Invalid credentials")
                    403 -> Exception("Access forbidden")
                    404 -> Exception("Resource not found")
                    500 -> Exception("Server error")
                    503 -> Exception("Service unavailable")
                    else -> Exception("Network error: ${exception.httpStatusCode}")
                }
            }
            is Failure.NetworkException -> Exception("Network connection error")
            is Failure.CryptoException -> Exception("Encryption error")
            else -> exception
        }
    }
}
