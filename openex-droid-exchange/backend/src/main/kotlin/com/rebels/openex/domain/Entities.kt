package com.rebels.openex.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class AccountType { USER, SYSTEM }
enum class Direction { DEBIT, CREDIT }
enum class Side { BUY, SELL }
enum class OrderType { LIMIT, MARKET }
enum class OrderStatus { OPEN, PARTIALLY_FILLED, FILLED, CANCELLED }

@Entity
@Table(name = "users")
class UserAccount(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true) var username: String = "",
    @Column(name = "password_hash", nullable = false) var passwordHash: String = "",
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "accounts")
class Account(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false) var userId: UUID = UUID.randomUUID(),
    @Column(nullable = false) var asset: String = "",
    @Enumerated(EnumType.STRING) @Column(nullable = false) var type: AccountType = AccountType.USER,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
)

/** Append-only. Never updated, never deleted. */
@Entity
@Table(name = "ledger_entries")
class LedgerEntry(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "transaction_id", nullable = false) var transactionId: UUID = UUID.randomUUID(),
    @Column(name = "account_id", nullable = false) var accountId: UUID = UUID.randomUUID(),
    @Column(nullable = false) var asset: String = "",
    @Enumerated(EnumType.STRING) @Column(nullable = false) var direction: Direction = Direction.DEBIT,
    @Column(nullable = false, precision = 38, scale = 18) var amount: BigDecimal = BigDecimal.ZERO,
    @Column var memo: String? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "orders")
class OrderEntity(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false) var userId: UUID = UUID.randomUUID(),
    @Column(nullable = false) var symbol: String = "",
    @Enumerated(EnumType.STRING) @Column(nullable = false) var side: Side = Side.BUY,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var type: OrderType = OrderType.LIMIT,
    @Column(precision = 38, scale = 18) var price: BigDecimal? = null,
    @Column(nullable = false, precision = 38, scale = 18) var quantity: BigDecimal = BigDecimal.ZERO,
    @Column(name = "filled_quantity", nullable = false, precision = 38, scale = 18)
    var filledQuantity: BigDecimal = BigDecimal.ZERO,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: OrderStatus = OrderStatus.OPEN,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
) {
    val remaining: BigDecimal get() = quantity.subtract(filledQuantity)
}

@Entity
@Table(name = "trades")
class Trade(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(nullable = false) var symbol: String = "",
    @Column(name = "buy_order_id", nullable = false) var buyOrderId: UUID = UUID.randomUUID(),
    @Column(name = "sell_order_id", nullable = false) var sellOrderId: UUID = UUID.randomUUID(),
    @Column(nullable = false, precision = 38, scale = 18) var price: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, precision = 38, scale = 18) var quantity: BigDecimal = BigDecimal.ZERO,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "idempotency_keys")
class IdempotencyRecord(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "idem_key", nullable = false) var idemKey: String = "",
    @Column(name = "user_id", nullable = false) var userId: UUID = UUID.randomUUID(),
    @Column(name = "request_hash", nullable = false) var requestHash: String = "",
    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT") var responseBody: String = "",
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
)
