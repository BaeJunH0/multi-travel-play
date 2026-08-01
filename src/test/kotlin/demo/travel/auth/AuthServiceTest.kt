package demo.travel.auth

import demo.travel.auth.application.AuthService
import demo.travel.auth.application.JwtProvider
import demo.travel.auth.client.GoogleOAuthClient
import demo.travel.auth.client.KakaoOAuthClient
import demo.travel.auth.client.dto.KakaoUserInfo
import demo.travel.auth.presentation.dto.AuthRequest
import demo.travel.user.AuthProvider
import demo.travel.user.User
import demo.travel.user.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.web.server.ResponseStatusException
import java.util.*
import java.util.concurrent.TimeUnit

class AuthServiceTest : BehaviorSpec({

    val userRepository = mockk<UserRepository>()
    val jwtProvider = mockk<JwtProvider>()
    val kakaoOAuthClient = mockk<KakaoOAuthClient>()
    val googleOAuthClient = mockk<GoogleOAuthClient>()
    val redisTemplate = mockk<StringRedisTemplate>()
    val opsForValue = mockk<ValueOperations<String, String>>()

    val service = AuthService(userRepository, jwtProvider, kakaoOAuthClient, googleOAuthClient, redisTemplate)

    val userId = UUID.randomUUID()
    val accessToken = "test.access.token"
    val refreshToken = "test.refresh.token"
    val encoder = BCryptPasswordEncoder()

    beforeEach {
        clearAllMocks()
        every { redisTemplate.opsForValue() } returns opsForValue
        every { jwtProvider.generate(any()) } returns accessToken
        every { jwtProvider.generateRefresh(any()) } returns refreshToken
        every { opsForValue.set(any(), any(), any<Long>(), any<TimeUnit>()) } just Runs
    }

    given("signup") {
        val request = AuthRequest.Signup("new@test.com", "password123", "홍길동")

        `when`("이메일이 중복되지 않을 때") {
            then("유저를 저장하고 TokenPair를 반환한다") {
                val savedUser = User(id = userId, email = request.email, nickname = request.nickname, provider = AuthProvider.LOCAL)
                every { userRepository.existsByEmail(request.email) } returns false
                every { userRepository.save(any()) } returns savedUser

                val result = service.signup(request)

                result.accessToken shouldBe accessToken
                result.refreshToken shouldBe refreshToken
                verify { userRepository.save(any()) }
            }
        }

        `when`("이메일이 이미 사용 중일 때") {
            then("409 CONFLICT를 던진다") {
                every { userRepository.existsByEmail(request.email) } returns true

                val ex = shouldThrow<ResponseStatusException> { service.signup(request) }
                ex.statusCode shouldBe HttpStatus.CONFLICT
                verify(exactly = 0) { userRepository.save(any()) }
            }
        }
    }

    given("login") {
        val encodedPassword = encoder.encode("correct123")
        val localUser = User(id = userId, email = "user@test.com", nickname = "유저", provider = AuthProvider.LOCAL, password = encodedPassword)

        `when`("이메일과 비밀번호가 올바를 때") {
            then("TokenPair를 반환한다") {
                val request = AuthRequest.Login("user@test.com", "correct123")
                every { userRepository.findByEmail(request.email) } returns localUser

                val result = service.login(request)

                result.accessToken shouldBe accessToken
                result.refreshToken shouldBe refreshToken
            }
        }

        `when`("이메일이 존재하지 않을 때") {
            then("401 UNAUTHORIZED를 던진다") {
                val request = AuthRequest.Login("none@test.com", "password123")
                every { userRepository.findByEmail(request.email) } returns null

                val ex = shouldThrow<ResponseStatusException> { service.login(request) }
                ex.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }

        `when`("비밀번호가 틀렸을 때") {
            then("401 UNAUTHORIZED를 던진다") {
                val request = AuthRequest.Login("user@test.com", "wrong123")
                every { userRepository.findByEmail(request.email) } returns localUser

                val ex = shouldThrow<ResponseStatusException> { service.login(request) }
                ex.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }

        `when`("소셜 로그인 계정 (password = null)일 때") {
            then("401 UNAUTHORIZED를 던진다") {
                val socialUser = User(id = userId, email = "kakao@kakao.local", nickname = "카카오유저", provider = AuthProvider.KAKAO)
                val request = AuthRequest.Login("kakao@kakao.local", "anything")
                every { userRepository.findByEmail(request.email) } returns socialUser

                val ex = shouldThrow<ResponseStatusException> { service.login(request) }
                ex.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }

    given("refresh") {
        `when`("Redis에 저장된 토큰과 일치할 때") {
            then("새 accessToken을 반환한다") {
                every { jwtProvider.parse(refreshToken) } returns userId
                every { opsForValue.get("refresh:$userId") } returns refreshToken

                val result = service.refresh(refreshToken)
                result shouldBe accessToken
            }
        }

        `when`("토큰 파싱에 실패할 때") {
            then("401 UNAUTHORIZED를 던진다") {
                every { jwtProvider.parse(any()) } returns null

                val ex = shouldThrow<ResponseStatusException> { service.refresh("invalid.token") }
                ex.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }

        `when`("Redis에 저장된 토큰과 다를 때") {
            then("401 UNAUTHORIZED를 던진다") {
                every { jwtProvider.parse(refreshToken) } returns userId
                every { opsForValue.get("refresh:$userId") } returns "other.stored.token"

                val ex = shouldThrow<ResponseStatusException> { service.refresh(refreshToken) }
                ex.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }

    given("logout") {
        `when`("유효한 refreshToken이 전달될 때") {
            then("Redis에서 토큰을 삭제한다") {
                every { jwtProvider.parse(refreshToken) } returns userId
                every { redisTemplate.delete(any<String>()) } returns true

                service.logout(refreshToken)

                verify { redisTemplate.delete("refresh:$userId") }
            }
        }

        `when`("파싱 불가한 토큰이 전달될 때") {
            then("Redis 삭제 없이 조용히 종료한다") {
                every { jwtProvider.parse(any()) } returns null

                service.logout("invalid.token")

                verify(exactly = 0) { redisTemplate.delete(any<String>()) }
            }
        }
    }

    given("kakaoLogin") {
        val kakaoToken = "kakao.access.token"
        val email = "kakao@email.com"

        `when`("신규 사용자일 때") {
            then("유저를 저장하고 TokenPair를 반환한다") {
                val newUser = User(id = userId, email = email, nickname = "카카오닉네임", provider = AuthProvider.KAKAO)
                every { kakaoOAuthClient.fetchAccessToken(any()) } returns kakaoToken
                every { kakaoOAuthClient.fetchUserInfo(kakaoToken) } returns KakaoUserInfo("12345", email, "카카오닉네임")
                every { userRepository.findByEmail(email) } returns null
                every { userRepository.save(any()) } returns newUser

                val result = service.kakaoLogin("auth.code")
                result.accessToken shouldBe accessToken
                verify { userRepository.save(any()) }
            }
        }

        `when`("기존 사용자일 때") {
            then("저장 없이 TokenPair를 반환한다") {
                val existingUser = User(id = userId, email = email, nickname = "기존유저", provider = AuthProvider.KAKAO)
                every { kakaoOAuthClient.fetchAccessToken(any()) } returns kakaoToken
                every { kakaoOAuthClient.fetchUserInfo(kakaoToken) } returns KakaoUserInfo("12345", email, "기존유저")
                every { userRepository.findByEmail(email) } returns existingUser

                val result = service.kakaoLogin("auth.code")
                result.accessToken shouldBe accessToken
                verify(exactly = 0) { userRepository.save(any()) }
            }
        }
    }
})
