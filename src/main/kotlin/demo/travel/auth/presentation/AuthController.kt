package demo.travel.auth.presentation

import demo.travel.auth.application.AuthService
import demo.travel.auth.application.dto.AuthCommand
import demo.travel.auth.application.dto.PasswordResetCommand
import demo.travel.auth.resolver.CurrentUser
import demo.travel.auth.client.GoogleOAuthClient
import demo.travel.auth.client.KakaoOAuthClient
import demo.travel.auth.application.PasswordResetService
import demo.travel.auth.presentation.dto.AuthRequest
import demo.travel.auth.presentation.dto.PasswordResetRequest
import demo.travel.auth.presentation.dto.TokenResponse
import demo.travel.auth.presentation.dto.UserResponse
import demo.travel.user.User
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.net.URI

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val kakaoOAuthClient: KakaoOAuthClient,
    private val googleOAuthClient: GoogleOAuthClient,
    private val passwordResetService: PasswordResetService,
) {
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(
        @Valid @RequestBody request: AuthRequest.Signup,
        response: HttpServletResponse,
    ): TokenResponse {
        val pair = authService.signup(AuthCommand.Signup(request.email, request.password, request.nickname))
        setRefreshCookie(response, pair.refreshToken)
        return TokenResponse(pair.accessToken)
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: AuthRequest.Login,
        response: HttpServletResponse,
    ): TokenResponse {
        val pair = authService.login(AuthCommand.Login(request.email, request.password))
        setRefreshCookie(response, pair.refreshToken)
        return TokenResponse(pair.accessToken)
    }

    @PostMapping("/refresh")
    fun refresh(request: HttpServletRequest): TokenResponse {
        val refreshToken = getRefreshCookie(request)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "refreshToken 쿠키가 없습니다.")
        return TokenResponse(authService.refresh(refreshToken))
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(request: HttpServletRequest, response: HttpServletResponse) {
        getRefreshCookie(request)?.let { authService.logout(it) }
        expireRefreshCookie(response)
    }

    @GetMapping("/me")
    fun me(@CurrentUser user: User) = UserResponse(
        id = user.id,
        email = user.email,
        nickname = user.nickname,
        provider = user.provider.name,
    )

    @GetMapping("/oauth2/google")
    fun googleRedirect(): ResponseEntity<Void> =
        ResponseEntity
            .status(HttpStatus.FOUND)
            .location(URI.create(googleOAuthClient.authorizationUrl()))
            .build()

    @GetMapping("/oauth2/google/callback")
    fun googleCallback(@RequestParam code: String, response: HttpServletResponse) {
        val pair = authService.googleLogin(code)
        setRefreshCookie(response, pair.refreshToken)
        response.addCookie(Cookie("accessToken", pair.accessToken).apply {
            path = "/"
            isHttpOnly = true
            maxAge = 3600
        })
        response.sendRedirect("/")
    }

    @GetMapping("/oauth2/kakao")
    fun kakaoRedirect(): ResponseEntity<Void> =
        ResponseEntity
            .status(HttpStatus.FOUND)
            .location(URI.create(kakaoOAuthClient.authorizationUrl()))
            .build()

    @GetMapping("/oauth2/kakao/callback")
    fun kakaoCallback(@RequestParam code: String, response: HttpServletResponse) {
        val pair = authService.kakaoLogin(code)
        setRefreshCookie(response, pair.refreshToken)
        response.addCookie(Cookie("accessToken", pair.accessToken).apply {
            path = "/"
            isHttpOnly = true
            maxAge = 3600
        })
        response.sendRedirect("/")
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.OK)
    fun requestPasswordReset(@Valid @RequestBody request: PasswordResetRequest.Request) {
        passwordResetService.requestReset(request.email)
    }

    @PostMapping("/password-reset/confirm")
    fun confirmPasswordReset(
        @Valid @RequestBody request: PasswordResetRequest.Confirm,
    ): TokenResponse {
        val accessToken = passwordResetService.confirmReset(
            PasswordResetCommand.Confirm(request.email, request.token, request.newPassword)
        )
        return TokenResponse(accessToken)
    }

    private fun setRefreshCookie(response: HttpServletResponse, token: String) {
        response.addCookie(Cookie("refreshToken", token).apply {
            path = "/api/auth"
            isHttpOnly = true
            maxAge = 60 * 60 * 24 * 7  // 7일
        })
    }

    private fun expireRefreshCookie(response: HttpServletResponse) {
        response.addCookie(Cookie("refreshToken", "").apply {
            path = "/api/auth"
            isHttpOnly = true
            maxAge = 0
        })
    }

    private fun getRefreshCookie(request: HttpServletRequest): String? =
        request.cookies?.find { it.name == "refreshToken" }?.value
}