package demo.travel.auth.dto

data class TokenResponse(val accessToken: String)

data class TokenPair(val accessToken: String, val refreshToken: String)
