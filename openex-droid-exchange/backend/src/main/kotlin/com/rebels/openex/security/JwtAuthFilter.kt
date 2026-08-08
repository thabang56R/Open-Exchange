package com.rebels.openex.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class JwtAuthFilter(private val jwtService: JwtService) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().authentication == null) {
            runCatching { jwtService.parseUserId(header.removePrefix("Bearer ").trim()) }
                .onSuccess { userId: UUID ->
                    val auth = UsernamePasswordAuthenticationToken(userId, null, emptyList())
                    auth.details = WebAuthenticationDetailsSource().buildDetails(request)
                    SecurityContextHolder.getContext().authentication = auth
                }
        }
        filterChain.doFilter(request, response)
    }
}

object CurrentUser {
    fun id(): UUID = SecurityContextHolder.getContext().authentication?.principal as? UUID
        ?: throw IllegalStateException("No authenticated user")
}
