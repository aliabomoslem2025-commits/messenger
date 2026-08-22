package com.matrixmessenger.data.matrix.mapper

import com.matrixmessenger.domain.model.DeliveryStatus
import com.matrixmessenger.domain.model.MessageType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.matrix.android.sdk.api.session.events.model.Event
import org.matrix.android.sdk.api.session.room.send.SendState
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.session.room.sender.SenderInfo

class MessageMapperTest {

    private val mapper = MessageMapper()
    private val currentUserId = "@me:matrix.org"

    @Test
    fun `mapToMatrixMessage should map SYNCED state to SENT when not read`() {
        val event = mockk<TimelineEvent> {
            every { root } returns mockk<Event> {
                every { eventId } returns "event_1"
                every { senderId } returns currentUserId
                every { originServerTs } returns 1000L
                every { sendState } returns SendState.SYNCED
                every { getClearType() } returns "m.room.message"
                every { getClearContent() } returns mapOf("msgtype" to "m.text", "body" to "Hello")
                every { type } returns "m.room.message"
                every { unsignedData } returns null
            }
            every { eventId } returns "event_1"
            every { roomId } returns "!room:matrix.org"
            every { senderInfo } returns SenderInfo(currentUserId, "Me", true, null)
            every { annotations } returns null
            every { localId } returns 123L
            every { readReceipts } returns emptyList()
        }

        val result = mapper.mapToMatrixMessage(event, currentUserId, latestOtherReadReceiptTs = 0L)
        
        assertEquals(DeliveryStatus.SENT, result.deliveryStatus)
    }

    @Test
    fun `mapToMatrixMessage should map SYNCED state to READ when read by others`() {
        val event = mockk<TimelineEvent> {
            every { root } returns mockk<Event> {
                every { eventId } returns "event_1"
                every { senderId } returns currentUserId
                every { originServerTs } returns 1000L
                every { sendState } returns SendState.SYNCED
                every { getClearType() } returns "m.room.message"
                every { getClearContent() } returns mapOf("msgtype" to "m.text", "body" to "Hello")
                every { type } returns "m.room.message"
                every { unsignedData } returns null
            }
            every { eventId } returns "event_1"
            every { roomId } returns "!room:matrix.org"
            every { senderInfo } returns SenderInfo(currentUserId, "Me", true, null)
            every { annotations } returns null
            every { localId } returns 123L
            every { readReceipts } returns emptyList()
        }

        // Suppose someone else read up to timestamp 1500
        val result = mapper.mapToMatrixMessage(event, currentUserId, latestOtherReadReceiptTs = 1500L)
        
        assertEquals(DeliveryStatus.READ, result.deliveryStatus)
    }

    @Test
    fun `mapToMatrixMessage should map FAILED state to FAILED`() {
        val event = mockk<TimelineEvent> {
            every { root } returns mockk<Event> {
                every { eventId } returns "local_1"
                every { senderId } returns currentUserId
                every { originServerTs } returns 1000L
                every { sendState } returns SendState.UNDELIVERED
                every { getClearType() } returns "m.room.message"
                every { getClearContent() } returns mapOf("msgtype" to "m.text", "body" to "Failed")
                every { type } returns "m.room.message"
                every { unsignedData } returns null
            }
            every { eventId } returns "local_1"
            every { roomId } returns "!room:matrix.org"
            every { senderInfo } returns SenderInfo(currentUserId, "Me", true, null)
            every { annotations } returns null
            every { localId } returns 123L
            every { readReceipts } returns emptyList()
        }

        val result = mapper.mapToMatrixMessage(event, currentUserId)
        
        assertEquals(DeliveryStatus.FAILED, result.deliveryStatus)
    }
}
