package demo.travel.ai.client.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ai.openai")
data class OpenAiProperties(
    val apiKey: String,
    val model: String,
)