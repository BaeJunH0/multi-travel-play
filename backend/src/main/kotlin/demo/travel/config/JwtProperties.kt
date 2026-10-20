package demo.travel.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** @param accessTokenExpiry, refreshTokenExpiry 밀리초 단위 */
@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(val secret: String, val accessTokenExpiry: Long, val refreshTokenExpiry: Long)
