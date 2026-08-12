package com.rebels.openex.web

import com.rebels.openex.service.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(AuthException::class)
    fun auth(ex: AuthException) = ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ApiError("unauthorized", ex.message))

    @ExceptionHandler(InsufficientFundsException::class)
    fun funds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(ApiError("insufficient_funds", ex.message))

    @ExceptionHandler(LedgerImbalanceException::class)
    fun ledger(ex: LedgerImbalanceException) = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiError("ledger_imbalance", ex.message))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun idem(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiError("idempotency_conflict", ex.message))

    @ExceptionHandler(OrderValidationException::class)
    fun order(ex: OrderValidationException) = ResponseEntity.badRequest()
        .body(ApiError("invalid_order", ex.message))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun header(ex: MissingRequestHeaderException) = ResponseEntity.badRequest()
        .body(ApiError("missing_header", ex.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(ex: MethodArgumentNotValidException) = ResponseEntity.badRequest()
        .body(ApiError("validation_error", ex.bindingResult.fieldErrors.joinToString { "${it.field}: ${it.defaultMessage}" }))
}
