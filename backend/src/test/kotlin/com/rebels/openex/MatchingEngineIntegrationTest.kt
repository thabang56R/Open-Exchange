package com.rebels.openex

import com.rebels.openex.domain.UserAccount
import com.rebels.openex.repo.UserRepository
import com.rebels.openex.service.LedgerService
import com.rebels.openex.service.OrderService
import com.rebels.openex.web.PlaceOrderRequest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class MatchingEngineIntegrationTest @Autowired constructor(
    private val orderService: OrderService,
    private val ledger: LedgerService,
    private val users: UserRepository,
) {
    private val symbol = "BTC-USD"

    private fun funded(name: String, usd: String, btc: String): UUID {
        val id = users.save(UserAccount(username = "$name-${UUID.randomUUID()}", passwordHash = "x")).id
        ledger.deposit(id, "USD", BigDecimal(usd))
        ledger.deposit(id, "BTC", BigDecimal(btc))
        return id
    }

    @Test
    fun `limit orders cross at the maker price and settle in the ledger`() {
        val seller = funded("seller", "0.000001", "5")
        val buyer = funded("buyer", "100000", "0.000001")

        orderService.place(seller, PlaceOrderRequest(symbol, "SELL", "LIMIT", BigDecimal("30000"), BigDecimal("1")))
        val taker = orderService.place(buyer, PlaceOrderRequest(symbol, "BUY", "LIMIT", BigDecimal("31000"), BigDecimal("1")))

        assertEquals("FILLED", taker.status)
        assertEquals(1, taker.trades.size)
        assertEquals(0, taker.trades[0].price.compareTo(BigDecimal("30000")))
        assertEquals(0, ledger.balance(buyer, "BTC").compareTo(BigDecimal("1.000001")))
        assertEquals(0, ledger.balance(seller, "USD").compareTo(BigDecimal("30000.000001")))
    }

    @Test
    fun `market order sweeps the book by price-time priority`() {
        val s1 = funded("ask1", "0.000001", "1")
        val s2 = funded("ask2", "0.000001", "1")
        val buyer = funded("mkt-buyer", "100000", "0.000001")

        orderService.place(s2, PlaceOrderRequest(symbol, "SELL", "LIMIT", BigDecimal("32000"), BigDecimal("1")))
        orderService.place(s1, PlaceOrderRequest(symbol, "SELL", "LIMIT", BigDecimal("31000"), BigDecimal("1")))

        val market = orderService.place(buyer, PlaceOrderRequest(symbol, "BUY", "MARKET", null, BigDecimal("2")))

        assertEquals("FILLED", market.status)
        assertEquals(2, market.trades.size)
        assertEquals(0, market.trades[0].price.compareTo(BigDecimal("31000")), "best ask fills first")
        assertEquals(0, market.trades[1].price.compareTo(BigDecimal("32000")))
    }

    @Test
    fun `ten concurrent orders match without corrupting the ledger`() {
        val makers = (1..5).map { funded("maker$it", "0.000001", "1") }
        val takers = (1..5).map { funded("taker$it", "50000", "0.000001") }

        val pool = Executors.newFixedThreadPool(10)
        val start = CountDownLatch(1)
        val done = CountDownLatch(10)

        makers.forEach { maker ->
            pool.submit {
                start.await()
                runCatching {
                    orderService.place(maker, PlaceOrderRequest(symbol, "SELL", "LIMIT", BigDecimal("29000"), BigDecimal("1")))
                }
                done.countDown()
            }
        }
        takers.forEach { taker ->
            pool.submit {
                start.await()
                Thread.sleep(50) // give the makers a head start so there is something to hit
                runCatching {
                    orderService.place(taker, PlaceOrderRequest(symbol, "BUY", "LIMIT", BigDecimal("29500"), BigDecimal("1")))
                }
                done.countDown()
            }
        }

        start.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS), "all submissions completed")
        pool.shutdown()

        val totalBtc = (makers + takers).sumOf { ledger.balance(it, "BTC") }
        val totalUsd = (makers + takers).sumOf { ledger.balance(it, "USD") }

        // Conservation of credits: nothing is created or destroyed by matching.
        assertEquals(0, totalBtc.compareTo(BigDecimal("5.000005")), "BTC conserved, got $totalBtc")
        assertEquals(0, totalUsd.compareTo(BigDecimal("250000.000005")), "USD conserved, got $totalUsd")
        assertTrue((makers + takers).all { ledger.balance(it, "USD") >= BigDecimal.ZERO }, "no negative balances")
    }
}
