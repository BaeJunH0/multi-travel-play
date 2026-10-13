package demo.travel.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "frontend")
data class FrontendProperties(
    val url: String,
)
