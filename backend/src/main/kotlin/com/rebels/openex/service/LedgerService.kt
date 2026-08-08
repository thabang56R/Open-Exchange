package com.rebels.openex.service

import com.rebels.openex.domain.*
import com.rebels.openex.repo.AccountRepository
import com.rebels.openex.repo.LedgerEntryRepository
import com.rebels.openex.repo.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

class LedgerImbalanceException(message: String) : RuntimeException(message)
class InsufficientFundsException(message: String) : RuntimeException(message)

/** One leg of a double-entry transaction. */
data class Leg(
    val accountId: UUID,
    val asset: String,
    val direction: Direction,
    val amount: BigDecimal,
    val memo: String? = null,
)

@Service
class LedgerService(
    private val accounts: AccountRepository,
    private val entries: LedgerEntryRepository,
    private val users: UserRepository,
) {
    companion object {
        const val SYSTEM_USERNAME = "system.faucet"
    }

    @Transactional
    fun systemUserId(): UUID = users.findByUsername(SYSTEM_USERNAME)
        .orElseGet {
            users.save(UserAccount(username = SYSTEM_USERNAME, passwordHash = "!"))
        }.id

    @Transactional
    fun accountOf(userId: UUID, asset: String, type: AccountType = AccountType.USER): Account =
        accounts.findByUserIdAndAsset(userId, asset)
            .orElseGet { accounts.save(Account(userId = userId, asset = asset, type = type)) }

    @Transactional(readOnly = true)
    fun balance(userId: UUID, asset: String): BigDecimal =
        accounts.findByUserIdAndAsset(userId, asset)
            .map { entries.balanceOf(it.id) }
            .orElse(BigDecimal.ZERO)

    @Transactional(readOnly = true)
    fun balances(userId: UUID): Map<String, BigDecimal> =
        accounts.findByUserId(userId).associate { it.asset to entries.balanceOf(it.id) }

    /**
     * Posts a balanced set of legs atomically. Debits and credits MUST net to zero per asset,
     * otherwise the whole transaction is rejected and rolled back.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    fun post(legs: List<Leg>, transactionId: UUID = UUID.randomUUID()): UUID {
        if (legs.size < 2) throw LedgerImbalanceException("A transaction requires at least two legs")

        legs.groupBy { it.asset }.forEach { (asset, assetLegs) ->
            val net = assetLegs.fold(BigDecimal.ZERO) { acc, leg ->
                if (leg.amount <= BigDecimal.ZERO) {
                    throw LedgerImbalanceException("Leg amount must be positive for $asset")
                }
                when (leg.direction) {
                    Direction.CREDIT -> acc.add(leg.amount)
                    Direction.DEBIT -> acc.subtract(leg.amount)
                }
            }
            if (net.compareTo(BigDecimal.ZERO) != 0) {
                throw LedgerImbalanceException("Unbalanced ledger transaction for $asset: net=$net")
            }
        }

        legs.forEach { leg ->
            entries.save(
                LedgerEntry(
                    transactionId = transactionId,
                    accountId = leg.accountId,
                    asset = leg.asset,
                    direction = leg.direction,
                    amount = leg.amount,
                    memo = leg.memo,
                )
            )
        }
        return transactionId
    }

    /** Faucet: system account is debited, user account credited. */
    @Transactional
    fun deposit(userId: UUID, asset: String, amount: BigDecimal, memo: String = "faucet deposit"): UUID {
        require(amount > BigDecimal.ZERO) { "Deposit must be positive" }
        val system = accountOf(systemUserId(), asset, AccountType.SYSTEM)
        val user = accountOf(userId, asset)
        return post(
            listOf(
                Leg(system.id, asset, Direction.DEBIT, amount, memo),
                Leg(user.id, asset, Direction.CREDIT, amount, memo),
            )
        )
    }

    /** Settles one trade: base moves seller -> buyer, quote moves buyer -> seller. */
    @Transactional
    fun settleTrade(
        buyerId: UUID,
        sellerId: UUID,
        baseAsset: String,
        quoteAsset: String,
        quantity: BigDecimal,
        price: BigDecimal,
        tradeId: UUID,
    ): UUID {
        val notional = price.multiply(quantity)
        val buyerQuote = accountOf(buyerId, quoteAsset)
        val sellerQuote = accountOf(sellerId, quoteAsset)
        val buyerBase = accountOf(buyerId, baseAsset)
        val sellerBase = accountOf(sellerId, baseAsset)

        if (entries.balanceOf(buyerQuote.id) < notional) {
            throw InsufficientFundsException("Buyer lacks $notional $quoteAsset")
        }
        if (entries.balanceOf(sellerBase.id) < quantity) {
            throw InsufficientFundsException("Seller lacks $quantity $baseAsset")
        }

        val memo = "trade $tradeId"
        return post(
            listOf(
                Leg(buyerQuote.id, quoteAsset, Direction.DEBIT, notional, memo),
                Leg(sellerQuote.id, quoteAsset, Direction.CREDIT, notional, memo),
                Leg(sellerBase.id, baseAsset, Direction.DEBIT, quantity, memo),
                Leg(buyerBase.id, baseAsset, Direction.CREDIT, quantity, memo),
            )
        )
    }
}
