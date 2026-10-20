package demo.travel.ai.client

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "google.places")
data class GooglePlacesProperties(val apiKey: String)
