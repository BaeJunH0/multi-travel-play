package demo.travel.auth.application.dto

object AuthCommand {
    data class Login(
        val email: String,
        val password: String,
    )

    data class Signup(
        val email: String,
        val password: String,
        val nickname: String,
    )
}
