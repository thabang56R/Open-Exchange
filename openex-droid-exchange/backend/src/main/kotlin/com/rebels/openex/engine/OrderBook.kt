package com.rebels.openex.engine

import java.math.BigDecimal
import java.time.Instant
import java.util.TreeMap
import java.util.UUID

/** In-memory resting order (price-time priority). */
data class RestingOrder(
    val orderId: UUID,
    val userId: UUID,
    val price: BigDecimal,
    var remaining: BigDecimal,
    val createdAt: Instant,
    val sequence: Long,
)

/**
 * Price-time priority book. Bids are sorted highest price first, asks lowest first;
 * within a price level orders are FIFO by arrival sequence.
 */
class OrderBook(val symbol: String) {

    val bids: TreeMap<BigDecimal, ArrayDeque<RestingOrder>> = TreeMap(reverseOrder())
    val asks: TreeMap<BigDecimal, ArrayDeque<RestingOrder>> = TreeMap()

    fun addBid(order: RestingOrder) = bids.computeIfAbsent(order.price) { ArrayDeque() }.addLast(order)
    fun addAsk(order: RestingOrder) = asks.computeIfAbsent(order.price) { ArrayDeque() }.addLast(order)

    fun bestBid(): BigDecimal? = bids.keys.firstOrNull()
    fun bestAsk(): BigDecimal? = asks.keys.firstOrNull()

    fun peekBid(): RestingOrder? = bids.firstEntry()?.value?.firstOrNull()
    fun peekAsk(): RestingOrder? = asks.firstEntry()?.value?.firstOrNull()

    fun pruneBids() = prune(bids)
    fun pruneAsks() = prune(asks)

    fun remove(orderId: UUID) {
        listOf(bids, asks).forEach { side ->
            side.values.forEach { queue -> queue.removeAll { it.orderId == orderId } }
        }
        prune(bids); prune(asks)
    }

    private fun prune(side: TreeMap<BigDecimal, ArrayDeque<RestingOrder>>) {
        side.values.forEach { queue -> while (queue.isNotEmpty() && queue.first().remaining <= BigDecimal.ZERO) queue.removeFirst() }
        side.entries.removeIf { it.value.isEmpty() }
    }

    fun depth(levels: Int): Pair<List<Pair<BigDecimal, BigDecimal>>, List<Pair<BigDecimal, BigDecimal>>> {
        fun agg(side: TreeMap<BigDecimal, ArrayDeque<RestingOrder>>) = side.entries.take(levels)
            .map { (price, queue) -> price to queue.fold(BigDecimal.ZERO) { a, o -> a.add(o.remaining) } }
            .filter { it.second > BigDecimal.ZERO }
        return agg(bids) to agg(asks)
    }
}
