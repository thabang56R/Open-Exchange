package com.rebels.openex.engine

import com.rebels.openex.domain.*
import com.rebels.openex.repo.OrderRepository
import com.rebels.openex.repo.TradeRepository
import com.rebels.openex.service.InsufficientFundsException
import com.rebels.openex.service.LedgerService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Core Execution Matrix: in-memory price-time priority matching engine.
 * All state mutation for a symbol happens under that symbol's lock, so concurrent
 * submissions are serialized per market and can never cross-fill twice.
 */
@Service
class MatchingEngineService(
    private val orders: OrderRepository,
    private val trades: TradeRepository,
    private val ledger: LedgerService,
    private val broadcaster: OrderBookBroadcaster,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val books = ConcurrentHashMap<String, OrderBook>()
    private val locks = ConcurrentHashMap<String, Any>()
    private val sequence = AtomicLong(0)

    fun book(symbol: String): OrderBook = books.computeIfAbsent(symbol) { OrderBook(it) }
    private fun lock(symbol: String): Any = locks.computeIfAbsent(symbol) { Any() }

    /** Rebuild the books from any still-open orders after a restart. */
    @PostConstruct
    @Transactional(readOnly = true)
    fun rehydrate() {
        orders.findAll()
            .filter { it.status == OrderStatus.OPEN || it.status == OrderStatus.PARTIALLY_FILLED }
            .filter { it.type == OrderType.LIMIT && it.price != null }
            .sortedBy { it.createdAt }
            .forEach { rest(it) }
    }

    private fun rest(order: OrderEntity) {
        val resting = RestingOrder(
            orderId = order.id,
            userId = order.userId,
            price = order.price!!,
            remaining = order.remaining,
            createdAt = order.createdAt,
            sequence = sequence.incrementAndGet(),
        )
        val b = book(order.symbol)
        if (order.side == Side.BUY) b.addBid(resting) else b.addAsk(resting)
    }

    /**
     * Matches an incoming order against the book, settling each fill through the ledger.
     * Must be called inside the caller's transaction so a settlement failure rolls back everything.
     */
    @Transactional
    fun submit(incoming: OrderEntity): List<Trade> = synchronized(lock(incoming.symbol)) {
        val (baseAsset, quoteAsset) = incoming.symbol.split("-").let { it[0] to it[1] }
        val book = book(incoming.symbol)
        val executed = mutableListOf<Trade>()

        while (incoming.remaining > BigDecimal.ZERO) {
            val counter = (if (incoming.side == Side.BUY) book.peekAsk() else book.peekBid()) ?: break
            if (counter.userId == incoming.userId) break // never self-trade
            if (!crosses(incoming, counter.price)) break

            val quantity = incoming.remaining.min(counter.remaining)
            val price = counter.price // resting order sets the price (maker priority)

            val trade = Trade(
                symbol = incoming.symbol,
                buyOrderId = if (incoming.side == Side.BUY) incoming.id else counter.orderId,
                sellOrderId = if (incoming.side == Side.SELL) incoming.id else counter.orderId,
                price = price,
                quantity = quantity,
            )

            val buyerId = if (incoming.side == Side.BUY) incoming.userId else counter.userId
            val sellerId = if (incoming.side == Side.SELL) incoming.userId else counter.userId

            try {
                ledger.settleTrade(buyerId, sellerId, baseAsset, quoteAsset, quantity, price, trade.id)
            } catch (ex: InsufficientFundsException) {
                // The counterparty (or taker) cannot honour this fill: drop the offending maker and continue.
                log.warn("Fill rejected for {}: {}", incoming.symbol, ex.message)
                counter.remaining = BigDecimal.ZERO
                cancelRestingOrder(counter.orderId)
                book.pruneBids(); book.pruneAsks()
                if (buyerId == incoming.userId || sellerId == incoming.userId) {
                    if (executed.isEmpty()) throw ex
                }
                continue
            }

            trades.save(trade)
            executed += trade

            incoming.filledQuantity = incoming.filledQuantity.add(quantity)
            counter.remaining = counter.remaining.subtract(quantity)

            val maker = orders.findById(counter.orderId).orElseThrow()
            maker.filledQuantity = maker.filledQuantity.add(quantity)
            maker.status = if (maker.remaining <= BigDecimal.ZERO) OrderStatus.FILLED else OrderStatus.PARTIALLY_FILLED
            orders.save(maker)

            book.pruneBids(); book.pruneAsks()
        }

        incoming.status = when {
            incoming.remaining <= BigDecimal.ZERO -> OrderStatus.FILLED
            incoming.type == OrderType.MARKET -> OrderStatus.CANCELLED // market remainder never rests
            incoming.filledQuantity > BigDecimal.ZERO -> OrderStatus.PARTIALLY_FILLED
            else -> OrderStatus.OPEN
        }
        orders.save(incoming)

        if (incoming.type == OrderType.LIMIT && incoming.remaining > BigDecimal.ZERO) rest(incoming)

        broadcaster.broadcast(book)
        executed
    }


    private fun cancelRestingOrder(orderId: UUID) {
        orders.findById(orderId).ifPresent {
            if (it.status == OrderStatus.OPEN || it.status == OrderStatus.PARTIALLY_FILLED) {
                it.status = OrderStatus.CANCELLED
                orders.save(it)
            }
        }
    }

    fun cancel(order: OrderEntity) = synchronized(lock(order.symbol)) {
        book(order.symbol).remove(order.id)
        order.status = OrderStatus.CANCELLED
        orders.save(order)
        broadcaster.broadcast(book(order.symbol))
    }


    private fun crosses(incoming: OrderEntity, restingPrice: BigDecimal): Boolean {
        if (incoming.type == OrderType.MARKET) return true
        val limit = incoming.price ?: return false
        return if (incoming.side == Side.BUY) limit >= restingPrice else limit <= restingPrice
    }
}
