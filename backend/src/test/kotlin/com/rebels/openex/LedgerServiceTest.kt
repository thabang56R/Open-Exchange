package com.rebels.openex

import com.rebels.openex.domain.AccountType
import com.rebels.openex.domain.Direction
import com.rebels.openex.repo.LedgerEntryRepository
import com.rebels.openex.repo.UserRepository
import com.rebels.openex.domain.UserAccount
import com.rebels.openex.service.Leg
import com.rebels.openex.service.LedgerImbalanceException
import com.rebels.openex.service.LedgerService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@SpringBootTest
class LedgerServiceTest @Autowired constructor(
    private val ledger: LedgerService,
    private val entries: LedgerEntryRepository,
    private val users: UserRepository,
) {
    private fun newUser(name: String) = users.save(UserAccount(username = name, passwordHash = "x")).id

    @Test
    fun `deposit credits the user and debits the system, netting to zero`() {
        val user = newUser("luke-${System.nanoTime()}")
        val txId = ledger.deposit(user, "USD", BigDecimal("1000"))

        val legs = entries.findByTransactionId(txId)
        assertEquals(2, legs.size)
        val net = legs.fold(BigDecimal.ZERO) { acc, e ->
            if (e.direction == Direction.CREDIT) acc.add(e.amount) else acc.subtract(e.amount)
        }
        assertEquals(0, net.compareTo(BigDecimal.ZERO), "Ledger entries must sum to zero")
        assertEquals(0, ledger.balance(user, "USD").compareTo(BigDecimal("1000")))
    }

    @Test
    fun `unbalanced transaction is rejected and nothing is persisted`() {
        val user = newUser("han-${System.nanoTime()}")
        val account = ledger.accountOf(user, "USD")
        val system = ledger.accountOf(ledger.systemUserId(), "USD", AccountType.SYSTEM)
        val before = entries.count()

        assertFailsWith<LedgerImbalanceException> {
            ledger.post(
                listOf(
                    Leg(system.id, "USD", Direction.DEBIT, BigDecimal("100")),
                    Leg(account.id, "USD", Direction.CREDIT, BigDecimal("99")),
                )
            )
        }
        assertEquals(before, entries.count(), "Rollback must leave the ledger untouched")
        assertEquals(0, ledger.balance(user, "USD").compareTo(BigDecimal.ZERO))
    }

    @Test
    fun `trade settlement moves both assets and stays balanced`() {
        val buyer = newUser("leia-${System.nanoTime()}")
        val seller = newUser("chewie-${System.nanoTime()}")
        ledger.deposit(buyer, "USD", BigDecimal("50000"))
        ledger.deposit(seller, "BTC", BigDecimal("2"))

        val txId = ledger.settleTrade(
            buyerId = buyer, sellerId = seller,
            baseAsset = "BTC", quoteAsset = "USD",
            quantity = BigDecimal("1"), price = BigDecimal("30000"),
            tradeId = java.util.UUID.randomUUID(),
        )

        val legs = entries.findByTransactionId(txId)
        assertEquals(4, legs.size)
        legs.groupBy { it.asset }.forEach { (_, assetLegs) ->
            val net = assetLegs.fold(BigDecimal.ZERO) { acc, e ->
                if (e.direction == Direction.CREDIT) acc.add(e.amount) else acc.subtract(e.amount)
            }
            assertEquals(0, net.compareTo(BigDecimal.ZERO))
        }
        assertEquals(0, ledger.balance(buyer, "BTC").compareTo(BigDecimal("1")))
        assertEquals(0, ledger.balance(seller, "USD").compareTo(BigDecimal("30000")))
        assertTrue(ledger.balance(buyer, "USD").compareTo(BigDecimal("20000")) == 0)
    }
}
