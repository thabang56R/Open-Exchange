package com.rebels.openex.repo

import com.rebels.openex.domain.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

interface UserRepository : JpaRepository<UserAccount, UUID> {
    fun findByUsername(username: String): Optional<UserAccount>
}

interface AccountRepository : JpaRepository<Account, UUID> {
    fun findByUserIdAndAsset(userId: UUID, asset: String): Optional<Account>
    fun findByUserId(userId: UUID): List<Account>
}

interface LedgerEntryRepository : JpaRepository<LedgerEntry, UUID> {
    fun findByTransactionId(transactionId: UUID): List<LedgerEntry>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN e.direction = com.rebels.openex.domain.Direction.CREDIT
                                 THEN e.amount ELSE -e.amount END), 0)
        FROM LedgerEntry e WHERE e.accountId = :accountId
        """
    )
    fun balanceOf(@Param("accountId") accountId: UUID): BigDecimal
}

interface OrderRepository : JpaRepository<OrderEntity, UUID> {
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<OrderEntity>
    fun findBySymbolAndStatusIn(symbol: String, statuses: Collection<OrderStatus>): List<OrderEntity>
}

interface TradeRepository : JpaRepository<Trade, UUID> {
    fun findTop50BySymbolOrderByCreatedAtDesc(symbol: String): List<Trade>
}

interface IdempotencyRepository : JpaRepository<IdempotencyRecord, UUID> {
    fun findByUserIdAndIdemKey(userId: UUID, idemKey: String): Optional<IdempotencyRecord>
}
