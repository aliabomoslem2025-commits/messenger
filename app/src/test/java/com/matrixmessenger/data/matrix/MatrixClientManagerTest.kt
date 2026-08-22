package com.matrixmessenger.data.matrix

import android.content.Context
import android.os.Looper
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import com.matrixmessenger.core.coroutine.DispatcherProvider
import com.matrixmessenger.data.local.AppPreferences
import io.mockk.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.matrix.android.sdk.api.Matrix
import org.matrix.android.sdk.api.auth.AuthenticationService
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.sync.SyncService

@OptIn(ExperimentalCoroutinesApi::class)
class MatrixClientManagerTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val context = mockk<Context>(relaxed = true)
    private val matrix = mockk<Matrix>()
    private val appPreferences = mockk<AppPreferences>(relaxed = true)
    private val authService = mockk<AuthenticationService>()
    private val session = mockk<Session>(relaxed = true)
    private val syncService = mockk<SyncService>(relaxed = true)

    private lateinit var manager: MatrixClientManager
    private val testDispatcher = StandardTestDispatcher()
    private val unconfinedDispatcher = UnconfinedTestDispatcher(testDispatcher.scheduler)

    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = unconfinedDispatcher
        override val io: CoroutineDispatcher = unconfinedDispatcher
        override val default: CoroutineDispatcher = unconfinedDispatcher
        override val unconfined: CoroutineDispatcher = unconfinedDispatcher
    }

    @Before
    fun setup() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        
        // Mock asFlow to avoid LiveData/Looper issues
        mockkStatic("androidx.lifecycle.FlowLiveDataConversions")
        every { any<LiveData<Any>>().asFlow() } returns flowOf(mockk<org.matrix.android.sdk.api.session.sync.SyncState>(relaxed = true))
        
        Dispatchers.setMain(testDispatcher)
        every { matrix.authenticationService() } returns authService
        every { session.syncService() } returns syncService
        every { session.myUserId } returns "@test:matrix.org"
        every { syncService.getSyncStateLive() } returns mockk(relaxed = true)
        
        manager = MatrixClientManager(context, matrix, appPreferences, testDispatcherProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Looper::class)
        unmockkStatic("androidx.lifecycle.FlowLiveDataConversions")
    }

    @Test
    fun `initialize should restore session if last session exists`() = runTest {
        coEvery { authService.getLastAuthenticatedSession() } returns session
        
        manager.initialize()
        advanceUntilIdle()
        
        val state = manager.sessionState.value
        assert(state is MatrixClientManager.SessionState.Active)
        assertEquals("@test:matrix.org", (state as MatrixClientManager.SessionState.Active).userId)
        verify { session.open() }
        verify { syncService.startSync(true) }
    }

    @Test
    fun `logout should clear session and state`() = runTest {
        coEvery { authService.getLastAuthenticatedSession() } returns session
        manager.initialize()
        advanceUntilIdle()
        
        coEvery { session.signOutService().signOut(true) } just Runs
        
        manager.logout()
        advanceUntilIdle()
        
        assertEquals(MatrixClientManager.SessionState.NoSession, manager.sessionState.value)
        assertEquals(null, manager.getCurrentSession())
        verify { session.close() }
    }
}
