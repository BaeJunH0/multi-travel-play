package demo.travel.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

object PasswordResetRequest {
    data class Request(
        @field:Email val email: String,
    )

    data class Confirm(
        @field:Email val email: String,
        @field:NotBlank val token: String,
        @field:NotBlank @field:Size(min = 8) val newPassword: String,
    )
}
