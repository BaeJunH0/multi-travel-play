package demo.travel.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spring.oauth2.google")
data class GoogleProperties(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
)
