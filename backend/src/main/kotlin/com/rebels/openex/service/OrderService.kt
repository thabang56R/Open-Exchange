package com.rebels.openex.service

import com.rebels.openex.domain.*
import com.rebels.openex.engine.MatchingEngineService
import com.rebels.openex.repo.OrderRepository
import com.rebels.openex.web.OrderResponse
import com.rebels.openex.web.PlaceOrderRequest
import com.rebels.openex.web.TradeResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

class OrderValidationException(message: String) : RuntimeException(message)

@Service
class OrderService(
    private val orders: OrderRepository,
    private val engine: MatchingEngineService,
    private val ledger: LedgerService,
) {
    @Transactional
    fun place(userId: UUID, req: PlaceOrderRequest): OrderResponse {
        val parts = req.symbol.split("-")
        if (parts.size != 2) throw OrderValidationException("Symbol must look like BASE-QUOTE")
        val (baseAsset, quoteAsset) = parts[0] to parts[1]

        val side = runCatching { Side.valueOf(req.side.uppercase()) }
            .getOrElse { throw OrderValidationException("side must be BUY or SELL") }
        val type = runCatching { OrderType.valueOf(req.type.uppercase()) }
            .getOrElse { throw OrderValidationException("type must be LIMIT or MARKET") }

        if (req.quantity <= BigDecimal.ZERO) throw OrderValidationException("quantity must be positive")
        if (type == OrderType.LIMIT && (req.price == null || req.price <= BigDecimal.ZERO)) {
            throw OrderValidationException("LIMIT orders require a positive price")
        }

        // Pre-trade funds check (the ledger enforces it again per fill).
        if (side == Side.SELL && ledger.balance(userId, baseAsset) < req.quantity) {
            throw InsufficientFundsException("Not enough $baseAsset to sell")
        }
        if (side == Side.BUY && type == OrderType.LIMIT) {
            val notional = req.price!!.multiply(req.quantity)
            if (ledger.balance(userId, quoteAsset) < notional) {
                throw InsufficientFundsException("Not enough $quoteAsset to buy")
            }
        }

        val order = orders.save(
            OrderEntity(
                userId = userId,
                symbol = req.symbol.uppercase(),
                side = side,
                type = type,
                price = if (type == OrderType.LIMIT) req.price else null,
                quantity = req.quantity,
                status = OrderStatus.OPEN,
            )
        )

        val executed = engine.submit(order)
        return order.toResponse(executed)
    }

    @Transactional(readOnly = true)
    fun listFor(userId: UUID): List<OrderResponse> =
        orders.findByUserIdOrderByCreatedAtDesc(userId).map { it.toResponse(emptyList()) }

    @Transactional
    fun cancel(userId: UUID, orderId: UUID): OrderResponse {
        val order = orders.findById(orderId).orElseThrow { OrderValidationException("Order not found") }
        if (order.userId != userId) throw OrderValidationException("Order not found")
        engine.cancel(order)
        return order.toResponse(emptyList())
    }
}

fun OrderEntity.toResponse(executed: List<Trade>) = OrderResponse(
    id = id,
    symbol = symbol,
    side = side.name,
    type = type.name,
    price = price,
    quantity = quantity,
    filledQuantity = filledQuantity,
    status = status.name,
    createdAt = createdAt,
    trades = executed.map { TradeResponse(it.id, it.symbol, it.price, it.quantity, it.createdAt) },
)
