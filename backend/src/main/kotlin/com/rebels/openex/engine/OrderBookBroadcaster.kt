package com.rebels.openex.engine

import com.rebels.openex.web.DepthLevel
import com.rebels.openex.web.OrderBookResponse
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

/**
 * Pushes an order book snapshot to /topic/orderbook and /topic/orderbook/{symbol}
 * every time the matching engine mutates a book.
 */
@Component
class OrderBookBroadcaster(private val messaging: SimpMessagingTemplate) {

    fun snapshot(book: OrderBook, levels: Int = 15): OrderBookResponse {
        val (bids, asks) = book.depth(levels)
        return OrderBookResponse(
            symbol = book.symbol,
            bids = bids.map { DepthLevel(it.first, it.second) },
            asks = asks.map { DepthLevel(it.first, it.second) },
        )
    }

    fun broadcast(book: OrderBook) {
        val payload = snapshot(book)
        messaging.convertAndSend("/topic/orderbook", payload)
        messaging.convertAndSend("/topic/orderbook/${book.symbol}", payload)
    }
}
