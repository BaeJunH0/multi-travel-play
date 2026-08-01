package demo.travel.auth.client

import demo.travel.auth.client.dto.GoogleUserInfo
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import com.fasterxml.jackson.annotation.JsonProperty
import java.net.http.HttpClient
import java.time.Duration

@Component
class GoogleOAuthClient(private val props: GoogleProperties) {

    private val authClient = RestClient.builder()
        .baseUrl("https://oauth2.googleapis.com")
        .requestFactory(requestFactory())
        .build()

    private val apiClient = RestClient.builder()
        .baseUrl("https://www.googleapis.com")
        .requestFactory(requestFactory())
        .build()

    fun authorizationUrl(): String =
        "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=${props.clientId}" +
                "&redirect_uri=${props.redirectUri}" +
                "&response_type=code" +
                "&scope=openid%20email%20profile"

    fun fetchAccessToken(code: String): String {
        val body = LinkedMultiValueMap<String, String>().apply {
            add("code", code)
            add("client_id", props.clientId)
            add("client_secret", props.clientSecret)
            add("redirect_uri", props.redirectUri)
            add("grant_type", "authorization_code")
        }
        return authClient.post()
            .uri("/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(body)
            .retrieve()
            .body(GoogleTokenResponse::class.java)!!
            .accessToken
    }

    fun fetchUserInfo(accessToken: String): GoogleUserInfo {
        val info = apiClient.get()
            .uri("/oauth2/v3/userinfo")
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(GoogleUserInfoResponse::class.java)!!

        return GoogleUserInfo(
            sub = info.sub,
            email = info.email,
            name = info.name ?: info.email?.substringBefore("@") ?: "${info.sub}@google.local",
        )
    }

    private data class GoogleTokenResponse(
        @JsonProperty("access_token") val accessToken: String,
    )

    private data class GoogleUserInfoResponse(
        val sub: String,
        val email: String?,
        val name: String?,
    )

    companion object {
        private fun requestFactory() = JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        ).apply { setReadTimeout(Duration.ofSeconds(10)) }
    }
}
