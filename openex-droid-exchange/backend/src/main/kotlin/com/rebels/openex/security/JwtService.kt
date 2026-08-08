package com.rebels.openex.security

import com.rebels.openex.config.OpenExProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtService(private val props: OpenExProperties) {

    private val key: SecretKey = Keys.hmacShaKeyFor(props.jwt.secret.toByteArray())

    fun issue(userId: UUID, username: String): String {
        val now = Date()
        val exp = Date(now.time + props.jwt.ttlMinutes * 60_000)
        return Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .issuedAt(now)
            .expiration(exp)
            .signWith(key)
            .compact()
    }

    fun parseUserId(token: String): UUID {
        val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        return UUID.fromString(claims.subject)
    }
}
