package com.rebels.openex.web

import com.rebels.openex.security.CurrentUser
import com.rebels.openex.service.AuthService
import com.rebels.openex.service.IdempotencyService
import com.rebels.openex.service.LedgerService
import com.rebels.openex.service.OrderService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/auth")
class AuthController(private val auth: AuthService) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody req: RegisterRequest) =
        TokenResponse(auth.register(req.username, req.password))

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest) =
        TokenResponse(auth.login(req.username, req.password))
}

@RestController
@RequestMapping("/api/wallets")
class WalletController(
    private val ledger: LedgerService,
    private val idempotency: IdempotencyService,
) {
    @PostMapping("/deposit")
    fun deposit(
        @RequestHeader("Idempotency-Key") idemKey: String,
        @Valid @RequestBody req: DepositRequest,
    ): BalanceResponse {
        val userId = CurrentUser.id()
        idempotency.cached(userId, idemKey, req, BalanceResponse::class.java)?.let { return it }
        ledger.deposit(userId, req.asset.uppercase(), req.amount)
        val response = BalanceResponse(ledger.balances(userId))
        idempotency.remember(userId, idemKey, req, response)
        return response
    }

    @GetMapping("/balances")
    fun balances() = BalanceResponse(ledger.balances(CurrentUser.id()))
}

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService,
    private val idempotency: IdempotencyService,
) {
    @PostMapping
    fun place(
        @RequestHeader("Idempotency-Key") idemKey: String,
        @Valid @RequestBody req: PlaceOrderRequest,
    ): ResponseEntity<OrderResponse> {
        val userId = CurrentUser.id()
        idempotency.cached(userId, idemKey, req, OrderResponse::class.java)?.let {
            return ResponseEntity.ok().header("Idempotent-Replay", "true").body(it)
        }
        val response = orderService.place(userId, req)
        idempotency.remember(userId, idemKey, req, response)
        return ResponseEntity.ok(response)
    }

    @GetMapping
    fun list() = orderService.listFor(CurrentUser.id())

    @DeleteMapping("/{id}")
    fun cancel(@PathVariable id: UUID) = orderService.cancel(CurrentUser.id(), id)
}

@RestController
@RequestMapping("/api/market")
class MarketDataController(private val engine: com.rebels.openex.engine.MatchingEngineService) {

    @GetMapping("/{symbol}/book")
    fun book(@PathVariable symbol: String, @RequestParam(defaultValue = "10") levels: Int): OrderBookResponse {
        val (bids, asks) = engine.book(symbol.uppercase()).depth(levels)
        return OrderBookResponse(
            symbol = symbol.uppercase(),
            bids = bids.map { DepthLevel(it.first, it.second) },
            asks = asks.map { DepthLevel(it.first, it.second) },
        )
    }
}
