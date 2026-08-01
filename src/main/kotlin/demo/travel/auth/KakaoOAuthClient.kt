package demo.travel.auth

import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import com.fasterxml.jackson.annotation.JsonProperty
import java.net.http.HttpClient
import java.time.Duration

@Component
class KakaoOAuthClient(private val props: KakaoProperties) {

    private val authClient = RestClient.builder()
        .baseUrl("https://kauth.kakao.com")
        .requestFactory(requestFactory())
        .build()

    private val apiClient = RestClient.builder()
        .baseUrl("https://kapi.kakao.com")
        .requestFactory(requestFactory())
        .build()

    fun authorizationUrl(): String =
        "https://kauth.kakao.com/oauth/authorize" +
                "?client_id=${props.clientId}" +
                "&redirect_uri=${props.redirectUri}" +
                "&response_type=code"

    fun fetchAccessToken(code: String): String {
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", props.clientId)
            add("client_secret", props.clientSecret)
            add("redirect_uri", props.redirectUri)
            add("code", code)
        }
        return authClient.post()
            .uri("/oauth/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(body)
            .retrieve()
            .body(KakaoTokenResponse::class.java)!!
            .accessToken
    }

    fun fetchUserInfo(accessToken: String): KakaoUserInfo {
        val me = apiClient.get()
            .uri("/v2/user/me")
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(KakaoMeResponse::class.java)!!

        return KakaoUserInfo(
            id = me.id.toString(),
            email = me.kakaoAccount.email,
            nickname = me.kakaoAccount.profile.nickname,
        )
    }

    private data class KakaoTokenResponse(
        @JsonProperty("access_token") val accessToken: String,
    )

    private data class KakaoMeResponse(
        val id: Long,
        @JsonProperty("kakao_account") val kakaoAccount: KakaoAccount,
    )

    private data class KakaoAccount(
        val email: String?,
        val profile: KakaoProfile,
    )

    private data class KakaoProfile(val nickname: String)

    companion object {
        private fun requestFactory() = JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        ).apply { setReadTimeout(Duration.ofSeconds(10)) }
    }
}

data class KakaoUserInfo(
    val id: String,
    val email: String?,
    val nickname: String,
)
