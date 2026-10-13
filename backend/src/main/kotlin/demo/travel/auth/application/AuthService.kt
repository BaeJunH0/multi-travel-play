package demo.travel.auth.application

import demo.travel.auth.client.GoogleOAuthClient
import demo.travel.auth.client.KakaoOAuthClient
import demo.travel.auth.application.dto.AuthCommand
import demo.travel.auth.application.dto.TokenPair
import demo.travel.user.AuthProvider
import demo.travel.user.User
import demo.travel.user.UserRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val jwtProvider: JwtProvider,
    private val kakaoOAuthClient: KakaoOAuthClient,
    private val googleOAuthClient: GoogleOAuthClient,
    private val redisTemplate: StringRedisTemplate,
) {
    private val passwordEncoder = BCryptPasswordEncoder()
    private val refreshTtlMs = 604_800_000L  // 7일

    fun signup(command: AuthCommand.Signup): TokenPair {
        if (userRepository.existsByEmail(command.email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.")
        }
        val user = userRepository.save(
            User(
                email = command.email,
                nickname = command.nickname,
                provider = AuthProvider.LOCAL,
                password = passwordEncoder.encode(command.password),
            )
        )
        return issueTokenPair(user.id)
    }

    @Transactional(readOnly = true)
    fun login(command: AuthCommand.Login): TokenPair {
        val user = userRepository.findByEmail(command.email)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.")
        if (user.password == null || !passwordEncoder.matches(command.password, user.password)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.")
        }
        return issueTokenPair(user.id)
    }

    fun kakaoLogin(code: String): TokenPair {
        val accessToken = kakaoOAuthClient.fetchAccessToken(code)
        val userInfo = kakaoOAuthClient.fetchUserInfo(accessToken)

        val email = userInfo.email ?: "${userInfo.id}@kakao.local"
        val user = userRepository.findByEmail(email) ?: userRepository.save(
            User(email = email, nickname = userInfo.nickname, provider = AuthProvider.KAKAO)
        )
        return issueTokenPair(user.id)
    }

    fun googleLogin(code: String): TokenPair {
        val accessToken = googleOAuthClient.fetchAccessToken(code)
        val userInfo = googleOAuthClient.fetchUserInfo(accessToken)

        val email = userInfo.email ?: "${userInfo.sub}@google.local"
        val user = userRepository.findByEmail(email) ?: userRepository.save(
            User(email = email, nickname = userInfo.name, provider = AuthProvider.GOOGLE)
        )
        return issueTokenPair(user.id)
    }

    fun refresh(refreshToken: String): String {
        val userId = jwtProvider.parse(refreshToken)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.")
        val stored = redisTemplate.opsForValue().get(refreshKey(userId))
        if (stored != refreshToken) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다.")
        return jwtProvider.generate(userId)
    }

    fun logout(refreshToken: String) {
        val userId = jwtProvider.parse(refreshToken) ?: return
        redisTemplate.delete(refreshKey(userId))
    }

    private fun issueTokenPair(userId: UUID): TokenPair {
        val accessToken = jwtProvider.generate(userId)
        val refreshToken = jwtProvider.generateRefresh(userId)
        redisTemplate.opsForValue().set(refreshKey(userId), refreshToken, refreshTtlMs, TimeUnit.MILLISECONDS)
        return TokenPair(accessToken, refreshToken)
    }

    private fun refreshKey(userId: UUID) = "refresh:$userId"
}