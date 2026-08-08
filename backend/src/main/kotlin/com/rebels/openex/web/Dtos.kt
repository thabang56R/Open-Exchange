package com.rebels.openex.web

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class RegisterRequest(
    @field:NotBlank @field:Size(min = 3, max = 64) val username: String = "",
    @field:NotBlank @field:Size(min = 8, max = 128) val password: String = "",
)

data class LoginRequest(
    @field:NotBlank val username: String = "",
    @field:NotBlank val password: String = "",
)

data class TokenResponse(val token: String, val tokenType: String = "Bearer")

data class DepositRequest(
    @field:NotBlank val asset: String = "",
    @field:NotNull @field:DecimalMin(value = "0.000000000000000001") val amount: BigDecimal = BigDecimal.ZERO,
)

data class BalanceResponse(val balances: Map<String, BigDecimal>)

data class PlaceOrderRequest(
    @field:NotBlank val symbol: String = "",
    @field:NotBlank val side: String = "",
    @field:NotBlank val type: String = "",
    val price: BigDecimal? = null,
    @field:NotNull @field:DecimalMin(value = "0.000000000000000001") val quantity: BigDecimal = BigDecimal.ZERO,
)

data class TradeResponse(
    val id: UUID,
    val symbol: String,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val createdAt: Instant,
)

data class OrderResponse(
    val id: UUID,
    val symbol: String,
    val side: String,
    val type: String,
    val price: BigDecimal?,
    val quantity: BigDecimal,
    val filledQuantity: BigDecimal,
    val status: String,
    val createdAt: Instant,
    val trades: List<TradeResponse> = emptyList(),
)

data class DepthLevel(val price: BigDecimal, val quantity: BigDecimal)
data class OrderBookResponse(val symbol: String, val bids: List<DepthLevel>, val asks: List<DepthLevel>)

data class ApiError(val error: String, val message: String?)
