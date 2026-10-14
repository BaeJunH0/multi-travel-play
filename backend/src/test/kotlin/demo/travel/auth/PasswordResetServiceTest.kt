package demo.travel.auth

import demo.travel.auth.application.JwtProvider
import demo.travel.auth.application.PasswordResetCodeStore
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
import org.springframework.http.HttpStatus
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

class PasswordResetServiceTest : BehaviorSpec({

    val userRepository = mockk<UserRepository>()
    val jwtProvider = mockk<JwtProvider>()
    val codeStore = mockk<PasswordResetCodeStore>()
    val mailSender = mockk<JavaMailSender>()

    val service = PasswordResetService(userRepository, jwtProvider, codeStore, mailSender)

    val email = "user@test.com"
    fun userWith(provider: AuthProvider) =
        User(email = email, nickname = "유저", provider = provider, password = "old-encoded")

    beforeEach {
        clearAllMocks()
        every { codeStore.save(any(), any()) } just Runs
        every { mailSender.send(any<SimpleMailMessage>()) } just Runs
        every { codeStore.remove(any()) } just Runs
        every { jwtProvider.generate(any()) } returns "access.token"
    }

    given("requestReset") {
        `when`("LOCAL 계정일 때") {
            then("코드를 저장하고 같은 코드를 메일로 보낸다") {
                every { userRepository.findByEmail(email) } returns userWith(AuthProvider.LOCAL)
                val code = slot<String>()
                every { codeStore.save(email, capture(code)) } just Runs

                service.requestReset(email)

                verify { codeStore.save(email, any()) }
                verify { mailSender.send(match<SimpleMailMessage> { it.to!!.contentEquals(arrayOf(email)) && it.text!!.contains(code.captured) }) }
            }
        }

        `when`("가입되지 않은 이메일일 때") {
            then("코드 저장·메일 발송 없이 조용히 끝난다") {
                every { userRepository.findByEmail(email) } returns null

                service.requestReset(email)

                verify(exactly = 0) { codeStore.save(any(), any()) }
                verify(exactly = 0) { mailSender.send(any<SimpleMailMessage>()) }
            }
        }

        `when`("소셜 계정일 때") {
            then("코드 저장·메일 발송 없이 조용히 끝난다") {
                every { userRepository.findByEmail(email) } returns userWith(AuthProvider.KAKAO)

                service.requestReset(email)

                verify(exactly = 0) { codeStore.save(any(), any()) }
                verify(exactly = 0) { mailSender.send(any<SimpleMailMessage>()) }
            }
        }
    }

    given("confirmReset") {
        val command = PasswordResetCommand.Confirm(email, "123456", "newPassword1")

        `when`("코드가 일치하는 LOCAL 계정일 때") {
            then("비밀번호를 바꾸고 코드를 삭제한 뒤 accessToken을 반환한다") {
                val user = userWith(AuthProvider.LOCAL)
                every { codeStore.find(email) } returns "123456"
                every { userRepository.findByEmail(email) } returns user

                val result = service.confirmReset(command)

                result shouldBe "access.token"
                user.password shouldNotBe "old-encoded"
                verify { codeStore.remove(email) }
            }
        }

        `when`("코드가 일치하지 않을 때") {
            then("400 INVALID_RESET_TOKEN") {
                every { codeStore.find(email) } returns "999999"

                val ex = shouldThrow<BusinessException> { service.confirmReset(command) }
                ex.code shouldBe "INVALID_RESET_TOKEN"
                ex.status shouldBe HttpStatus.BAD_REQUEST
            }
        }

        `when`("가입되지 않은 이메일일 때") {
            then("코드 불일치와 같은 400 INVALID_RESET_TOKEN") {
                every { codeStore.find(email) } returns null
                every { userRepository.findByEmail(email) } returns null

                val ex = shouldThrow<BusinessException> { service.confirmReset(command) }
                ex.code shouldBe "INVALID_RESET_TOKEN"
                ex.status shouldBe HttpStatus.BAD_REQUEST
            }
        }

        `when`("소셜 계정일 때") {
            then("코드 불일치와 같은 400 INVALID_RESET_TOKEN, 비밀번호는 바뀌지 않는다") {
                val user = userWith(AuthProvider.GOOGLE)
                every { codeStore.find(email) } returns "123456"
                every { userRepository.findByEmail(email) } returns user

                val ex = shouldThrow<BusinessException> { service.confirmReset(command) }
                ex.code shouldBe "INVALID_RESET_TOKEN"
                user.password shouldBe "old-encoded"
                verify(exactly = 0) { codeStore.remove(any()) }
            }
        }
    }
})
