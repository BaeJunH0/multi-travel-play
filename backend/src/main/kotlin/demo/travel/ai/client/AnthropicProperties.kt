package demo.travel.ai.client

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ai.anthropic")
data class AnthropicProperties(val apiKey: String, val model: String)
