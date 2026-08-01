package demo.travel.auth.presentation.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

object AuthRequest {
    data class Login(
        @field:Email val email: String,
        @field:NotBlank val password: String,
    )

    data class Signup(
        @field:Email val email: String,
        @field:NotBlank @field:Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.") val password: String,
        @field:NotBlank val nickname: String,
    )
}