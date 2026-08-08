package com.rebels.openex.service

import com.rebels.openex.domain.UserAccount
import com.rebels.openex.repo.UserRepository
import com.rebels.openex.security.JwtService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class AuthException(message: String) : RuntimeException(message)

@Service
class AuthService(
    private val users: UserRepository,
    private val encoder: PasswordEncoder,
    private val jwt: JwtService,
) {
    @Transactional
    fun register(username: String, password: String): String {
        if (users.findByUsername(username).isPresent) throw AuthException("Username already taken")
        val user = users.save(UserAccount(username = username, passwordHash = encoder.encode(password)))
        return jwt.issue(user.id, user.username)
    }

    @Transactional(readOnly = true)
    fun login(username: String, password: String): String {
        val user = users.findByUsername(username).orElseThrow { AuthException("Invalid credentials") }
        if (!encoder.matches(password, user.passwordHash)) throw AuthException("Invalid credentials")
        return jwt.issue(user.id, user.username)
    }
}
