package demo.travel.auth.presentation.dto

import java.util.UUID

data class UserResponse(
    val id: UUID,
    val email: String,
    val nickname: String,
    val provider: String,
)