package demo.travel.auth

import demo.travel.auth.dto.AuthRequest
import demo.travel.auth.dto.PasswordResetRequest
import demo.travel.auth.dto.TokenResponse
import demo.travel.auth.dto.UserResponse
import demo.travel.user.User
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
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
        val pair = authService.signup(request)
        setRefreshCookie(response, pair.refreshToken)
        return TokenResponse(pair.accessToken)
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: AuthRequest.Login,
        response: HttpServletResponse,
    ): TokenResponse {
        val pair = authService.login(request)
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
    fun googleRedirect(): org.springframework.http.ResponseEntity<Void> =
        org.springframework.http.ResponseEntity
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
    fun kakaoRedirect(): org.springframework.http.ResponseEntity<Void> =
        org.springframework.http.ResponseEntity
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
        val accessToken = passwordResetService.confirmReset(request)
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
