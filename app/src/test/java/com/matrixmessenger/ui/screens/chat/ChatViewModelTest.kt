package com.matrixmessenger.ui.screens.chat

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.matrixmessenger.feature.chat.presentation.ChatViewModel
import com.matrixmessenger.feature.chat.presentation.ChatUiState
import com.matrixmessenger.core.designsystem.components.ComposerState
import com.matrixmessenger.domain.model.*
import com.matrixmessenger.domain.repository.MatrixRepository
import com.matrixmessenger.feature.voice.data.recorder.AudioRecorder
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val matrixRepository = mockk<MatrixRepository>()
    private val audioRecorder = mockk<AudioRecorder>()
    private val savedStateHandle = SavedStateHandle(mapOf("roomId" to "!test_room_id"))
    private lateinit var viewModel: ChatViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        
        Dispatchers.setMain(testDispatcher)
        
        // Mock default message flow
        val mockMessages = listOf(
            MatrixMessage("1", "!test_room_id", "@user1", "Name 1", null, "Msg 1", null, MessageType.TEXT, Date(1000)),
            MatrixMessage("2", "!test_room_id", "@user1", "Name 1", null, "Msg 2", null, MessageType.TEXT, Date(2000))
        )
        every { matrixRepository.observeMessages("!test_room_id") } returns flowOf(mockMessages)
        coEvery { matrixRepository.markRoomAsRead("!test_room_id") } returns Result.success(Unit)
        
        viewModel = ChatViewModel(matrixRepository, audioRecorder, savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Looper::class)
        clearAllMocks()
    }

    @Test
    fun `init should observe messages from repository`() = runTest {
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.messages.size)
        assertEquals("Msg 1", viewModel.uiState.value.messages[0].body)
    }

    @Test
    fun `sendMessage should call repository and clear input on success`() = runTest {
        coEvery { 
            matrixRepository.sendMessage(any(), any(), any(), any(), any()) 
        } returns Result.success("event_id")

        viewModel.updateMessageInput("Hello")
        viewModel.sendMessage()
        
        advanceUntilIdle()
        
        val state = viewModel.uiState.value.composerState
        assert(state is ComposerState.Idle)
        coVerify { matrixRepository.sendMessage("!test_room_id", "Hello", null, any(), null) }
    }

    @Test
    fun `recording lifecycle should update composerState correctly`() = runTest {
        val tempDir = System.getProperty("java.io.tmpdir")
        val cacheDir = File(tempDir, "chat_vm_test")
        cacheDir.mkdirs()
        
        every { audioRecorder.prepare(any()) } returns Result.success(Unit)
        every { audioRecorder.start() } returns Result.success(Unit)
        every { audioRecorder.isRecording } returns true
        every { audioRecorder.getDurationMillis() } returns 500L
        every { audioRecorder.getNormalizedAmplitude() } returns 0.5f

        viewModel.startRecording(cacheDir)
        
        // Assert it started recording (state might transition immediately to RecordingVoice with UnconfinedTestDispatcher)
        var state = viewModel.uiState.value.composerState
        assert(state is ComposerState.RecordingVoice)
        
        advanceTimeBy(200) // Trigger ticker
        
        state = viewModel.uiState.value.composerState
        assert(state is ComposerState.RecordingVoice)
        assertEquals(500L, (state as ComposerState.RecordingVoice).durationMs)
    }

    @Test
    fun `stopAndSendRecording should call repository with correct duration`() = runTest {
        val tempDir = System.getProperty("java.io.tmpdir")
        val cacheDir = File(tempDir, "chat_vm_test_stop")
        cacheDir.mkdirs()
        val audioFile = File(cacheDir, "test.m4a")
        audioFile.createNewFile()

        every { audioRecorder.prepare(any()) } returns Result.success(Unit)
        every { audioRecorder.start() } returns Result.success(Unit)
        every { audioRecorder.stop() } returns audioFile.absolutePath
        every { audioRecorder.getDurationMillis() } returns 1234L
        every { audioRecorder.getNormalizedAmplitude() } returns 0.5f
        
        coEvery { 
            matrixRepository.sendAudioMessage(any(), any(), any(), any()) 
        } returns Result.success("event_id")

        viewModel.startRecording(cacheDir)
        advanceTimeBy(100) // Ticker updates duration
        
        viewModel.stopAndSendRecording()
        advanceUntilIdle()
        
        coVerify { 
            matrixRepository.sendAudioMessage("!test_room_id", match { it.absolutePath == audioFile.absolutePath }, 1234L, null) 
        }
    }

    @Test
    fun `retryMessage should call repository resendMessage`() = runTest {
        val message = MatrixMessage(
            eventId = "local_id",
            roomId = "!test_room_id",
            senderId = "@me",
            senderDisplayName = "Me",
            senderAvatarUrl = null,
            body = "Failed message",
            formattedBody = null,
            messageType = MessageType.TEXT,
            timestamp = Date(),
            deliveryStatus = DeliveryStatus.FAILED,
            localId = "local_id"
        )
        
        coEvery { matrixRepository.resendMessage("!test_room_id", "local_id") } returns Result.success(Unit)
        
        viewModel.retryMessage(message)
        
        advanceUntilIdle()
        coVerify { matrixRepository.resendMessage("!test_room_id", "local_id") }
        assertEquals(null, viewModel.uiState.value.selectedMessageForActions)
    }

    @Test
    fun `cancelMessage should call repository cancelSend`() = runTest {
        val message = MatrixMessage(
            eventId = "local_id",
            roomId = "!test_room_id",
            senderId = "@me",
            senderDisplayName = "Me",
            senderAvatarUrl = null,
            body = "Failed message",
            formattedBody = null,
            messageType = MessageType.TEXT,
            timestamp = Date(),
            deliveryStatus = DeliveryStatus.FAILED,
            localId = "local_id"
        )
        
        coEvery { matrixRepository.cancelSend("!test_room_id", "local_id") } returns Result.success(Unit)
        
        viewModel.cancelMessage(message)
        
        advanceUntilIdle()
        coVerify { matrixRepository.cancelSend("!test_room_id", "local_id") }
        assertEquals(null, viewModel.uiState.value.selectedMessageForActions)
    }
}
