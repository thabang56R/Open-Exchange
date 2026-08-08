package com.rebels.openex

import com.fasterxml.jackson.databind.ObjectMapper
import com.rebels.openex.web.DepositRequest
import com.rebels.openex.web.PlaceOrderRequest
import com.rebels.openex.web.RegisterRequest
import com.rebels.openex.web.TokenResponse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class OrderApiIdempotencyTest @Autowired constructor(
    private val mvc: MockMvc,
    private val mapper: ObjectMapper,
) {
    private fun token(): String {
        val body = mvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(RegisterRequest("trader-${UUID.randomUUID()}", "rebelbase1")))
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return mapper.readValue(body, TokenResponse::class.java).token
    }

    @Test
    fun `same idempotency key does not create a duplicate order`() {
        val jwt = token()

        mvc.perform(
            post("/api/wallets/deposit").header("Authorization", "Bearer $jwt")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(DepositRequest("USD", BigDecimal("100000"))))
        ).andExpect(status().isOk)

        val key = UUID.randomUUID().toString()
        val order = PlaceOrderRequest("BTC-USD", "BUY", "LIMIT", BigDecimal("25000"), BigDecimal("1"))

        val first = mvc.perform(
            post("/api/orders").header("Authorization", "Bearer $jwt")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(order))
        ).andExpect(status().isOk).andReturn().response.contentAsString

        val second = mvc.perform(
            post("/api/orders").header("Authorization", "Bearer $jwt")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(order))
        ).andExpect(status().isOk).andExpect(jsonPath("$.id").exists()).andReturn().response.contentAsString

        assertEquals(
            mapper.readTree(first).get("id").asText(),
            mapper.readTree(second).get("id").asText(),
            "Replay must return the cached order, not a new one",
        )

        mvc.perform(get("/api/orders").header("Authorization", "Bearer $jwt"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `unauthenticated requests are rejected`() {
        mvc.perform(get("/api/wallets/balances")).andExpect(status().is4xxClientError)
    }
}
