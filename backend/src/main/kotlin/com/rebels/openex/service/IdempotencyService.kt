package com.rebels.openex.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.rebels.openex.domain.IdempotencyRecord
import com.rebels.openex.repo.IdempotencyRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.UUID

class IdempotencyConflictException(message: String) : RuntimeException(message)

@Service
class IdempotencyService(
    private val repo: IdempotencyRepository,
    private val mapper: ObjectMapper,
) {
    private fun hash(payload: Any): String {
        val bytes = mapper.writeValueAsBytes(payload)
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /** Returns the cached response for (user, key) when the same request was already processed. */
    @Transactional(readOnly = true)
    fun <T> cached(userId: UUID, key: String, request: Any, responseType: Class<T>): T? {
        val record = repo.findByUserIdAndIdemKey(userId, key).orElse(null) ?: return null
        if (record.requestHash != hash(request)) {
            throw IdempotencyConflictException("Idempotency-Key reused with a different request body")
        }
        return mapper.readValue(record.responseBody, responseType)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun remember(userId: UUID, key: String, request: Any, response: Any) {
        try {
            repo.save(
                IdempotencyRecord(
                    idemKey = key,
                    userId = userId,
                    requestHash = hash(request),
                    responseBody = mapper.writeValueAsString(response),
                )
            )
        } catch (_: DataIntegrityViolationException) {
            // A concurrent request stored it first: that is fine, the cached response wins.
        }
    }
}
