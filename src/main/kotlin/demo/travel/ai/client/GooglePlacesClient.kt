package demo.travel.ai.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Component
class GooglePlacesClient(
    @Value("\${google.places.api-key}") private val apiKey: String,
) {
    private val log = LoggerFactory.getLogger(GooglePlacesClient::class.java)

    private val restClient = RestClient.builder()
        .baseUrl("https://maps.googleapis.com")
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
            ).apply { setReadTimeout(Duration.ofSeconds(10)) }
        )
        .build()

    fun findLatLng(placeName: String): Pair<Double, Double>? = runCatching {
        val response = restClient.get()
            .uri {
                it.path("/maps/api/place/findplacefromtext/json")
                    .queryParam("input", placeName)
                    .queryParam("inputtype", "textquery")
                    .queryParam("fields", "geometry")
                    .queryParam("key", apiKey)
                    .build()
            }
            .retrieve()
            .body(PlacesResponse::class.java)

        response?.candidates?.firstOrNull()?.geometry?.location?.let { it.lat to it.lng }
    }.onFailure { log.warn("Google Places 조회 실패: placeName={}", placeName, it) }
     .getOrNull()

    private data class PlacesResponse(val candidates: List<Candidate>)
    private data class Candidate(val geometry: Geometry?)
    private data class Geometry(val location: Location)
    private data class Location(val lat: Double, val lng: Double)
}