package demo.travel.auth

import demo.travel.auth.application.JwtProvider
import demo.travel.auth.application.PasswordResetService
import demo.travel.auth.application.dto.PasswordResetCommand
import demo.travel.common.exception.BusinessException
import demo.travel.user.AuthProvider
import demo.travel.user.User
import demo.travel.user.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpStatus
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import java.util.concurrent.TimeUnit

class PasswordResetServiceTest : BehaviorSpec({

    val userRepository = mockk<UserRepository>()
    val jwtProvider = mockk<JwtProvider>()
    val redisTemplate = mockk<StringRedisTemplate>()
    val opsForValue = mockk<ValueOperations<String, String>>()
    val mailSender = mockk<JavaMailSender>()

    val service = PasswordResetService(userRepository, jwtProvider, redisTemplate, mailSender)

    val email = "user@test.com"
    val key = "password-reset:$email"
    fun userWith(provider: AuthProvider) =
        User(email = email, nickname = "유저", provider = provider, password = "old-encoded")

    beforeEach {
        clearAllMocks()
        every { redisTemplate.opsForValue() } returns opsForValue
        every { opsForValue.set(any(), any(), any<Long>(), any<TimeUnit>()) } just Runs
        every { mailSender.send(any<SimpleMailMessage>()) } just Runs
        every { redisTemplate.delete(any<String>()) } returns true
        every { jwtProvider.generate(any()) } returns "access.token"
    }

    given("requestReset") {
        `when`("LOCAL 계정일 때") {
            then("코드를 Redis에 15분간 저장하고 메일을 보낸다") {
                every { userRepository.findByEmail(email) } returns userWith(AuthProvider.LOCAL)

                service.requestReset(email)

                verify { opsForValue.set(key, any(), 15, TimeUnit.MINUTES) }
                verify { mailSender.send(match<SimpleMailMessage> { it.to!!.contentEquals(arrayOf(email)) }) }
            }
        }

        `when`("가입되지 않은 이메일일 때") {
            then("코드 저장·메일 발송 없이 조용히 끝난다") {
                every { userRepository.findByEmail(email) } returns null

                service.requestReset(email)

                verify(exactly = 0) { opsForValue.set(any(), any(), any<Long>(), any<TimeUnit>()) }
                verify(exactly = 0) { mailSender.send(any<SimpleMailMessage>()) }
            }
        }

        `when`("소셜 계정일 때") {
            then("코드 저장·메일 발송 없이 조용히 끝난다") {
                every { userRepository.findByEmail(email) } returns userWith(AuthProvider.KAKAO)

                service.requestReset(email)

                verify(exactly = 0) { opsForValue.set(any(), any(), any<Long>(), any<TimeUnit>()) }
                verify(exactly = 0) { mailSender.send(any<SimpleMailMessage>()) }
            }
        }
    }

    given("confirmReset") {
        val command = PasswordResetCommand.Confirm(email, "123456", "newPassword1")

        `when`("코드가 일치하는 LOCAL 계정일 때") {
            then("비밀번호를 바꾸고 코드를 삭제한 뒤 accessToken을 반환한다") {
                val user = userWith(AuthProvider.LOCAL)
                every { opsForValue.get(key) } returns "123456"
                every { userRepository.findByEmail(email) } returns user

                val result = service.confirmReset(command)

                result shouldBe "access.token"
                user.password shouldNotBe "old-encoded"
                verify { redisTemplate.delete(key) }
            }
        }

        `when`("코드가 일치하지 않을 때") {
            then("400 INVALID_RESET_TOKEN") {
                every { opsForValue.get(key) } returns "999999"

                val ex = shouldThrow<BusinessException> { service.confirmReset(command) }
                ex.code shouldBe "INVALID_RESET_TOKEN"
                ex.status shouldBe HttpStatus.BAD_REQUEST
            }
        }

        `when`("가입되지 않은 이메일일 때") {
            then("코드 불일치와 같은 400 INVALID_RESET_TOKEN") {
                every { opsForValue.get(key) } returns null
                every { userRepository.findByEmail(email) } returns null

                val ex = shouldThrow<BusinessException> { service.confirmReset(command) }
                ex.code shouldBe "INVALID_RESET_TOKEN"
                ex.status shouldBe HttpStatus.BAD_REQUEST
            }
        }

        `when`("소셜 계정일 때") {
            then("코드 불일치와 같은 400 INVALID_RESET_TOKEN, 비밀번호는 바뀌지 않는다") {
                val user = userWith(AuthProvider.GOOGLE)
                every { opsForValue.get(key) } returns "123456"
                every { userRepository.findByEmail(email) } returns user

                val ex = shouldThrow<BusinessException> { service.confirmReset(command) }
                ex.code shouldBe "INVALID_RESET_TOKEN"
                user.password shouldBe "old-encoded"
                verify(exactly = 0) { redisTemplate.delete(any<String>()) }
            }
        }
    }
})
