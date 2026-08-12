package com.rebels.openex.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(OpenExProperties::class)
class PropertiesConfig

@ConfigurationProperties(prefix = "openex")
data class OpenExProperties(
    var jwt: Jwt = Jwt(),
    var markets: List<String> = listOf("BTC-USD"),
) {
    data class Jwt(
        var secret: String = "change-me-change-me-change-me-change-me-change-me-change-me",
        var ttlMinutes: Long = 130,
    )
}
