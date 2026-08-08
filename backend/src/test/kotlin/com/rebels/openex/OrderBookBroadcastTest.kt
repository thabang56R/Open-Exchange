package com.rebels.openex

import com.rebels.openex.engine.OrderBook
import com.rebels.openex.engine.OrderBookBroadcaster
import com.rebels.openex.engine.RestingOrder
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/** Day 6 deliverable: the engine emits JSON order book snapshots over STOMP. */
class OrderBookBroadcastTest {

    private fun resting(price: String, qty: String) = RestingOrder(
        orderId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        price = BigDecimal(price),
        remaining = BigDecimal(qty),
        createdAt = Instant.now(),
        sequence = 1,
    )

    @Test
    fun `broadcast publishes a sorted snapshot to both topics`() {
        val messaging = mock(SimpMessagingTemplate::class.java)
        val broadcaster = OrderBookBroadcaster(messaging)

        val book = OrderBook("BTC-USD").apply {
            addBid(resting("29000", "2"))
            addBid(resting("29500", "1"))
            addAsk(resting("30500", "3"))
            addAsk(resting("30000", "1"))
        }

        val expected = broadcaster.snapshot(book)
        assertEquals(0, expected.bids.first().price.compareTo(BigDecimal("29500"))) // best bid first
        assertEquals(0, expected.asks.first().price.compareTo(BigDecimal("30000"))) // best ask first
        assertEquals("BTC-USD", expected.symbol)

        broadcaster.broadcast(book)

        verify(messaging).convertAndSend("/topic/orderbook", expected as Any)
        verify(messaging).convertAndSend("/topic/orderbook/BTC-USD", expected as Any)
    }
}
