package demo.travel.auth.client.dto

data class GoogleUserInfo(
    val sub: String,
    val email: String?,
    val name: String,
)
