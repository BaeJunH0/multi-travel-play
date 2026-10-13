package demo.travel.ai.client.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ai.anthropic")
data class AnthropicProperties(
    val apiKey: String,
    val model: String,
)
