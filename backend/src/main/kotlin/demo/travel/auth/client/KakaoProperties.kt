package demo.travel.auth.client

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spring.oauth2.kakao")
data class KakaoProperties(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
)