package demo.travel.auth.application.dto

object PasswordResetCommand {
    data class Confirm(
        val email: String,
        val token: String,
        val newPassword: String,
    )
}
