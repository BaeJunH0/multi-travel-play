package demo.travel.auth

import demo.travel.auth.application.AuthService
import demo.travel.auth.application.dto.AuthCommand
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import java.util.*

@SpringBootTest
class RefreshTokenPersistenceTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    lateinit var authService: AuthService

    init {
        given("refreshTokenRepository.save") {
            `when`("같은 유저로 다시 저장할 때") {
                then("행을 새로 만들지 않고 토큰을 덮어쓴다") {
                    val userId = UUID.randomUUID()
                    refreshTokenRepository.save(RefreshToken(userId, "first.token"))
                    refreshTokenRepository.save(RefreshToken(userId, "second.token"))

                    refreshTokenRepository.findByIdOrNull(userId)!!.token shouldBe "second.token"
                }
            }
        }

        given("authService.login") {
            `when`("이메일 로그인에 성공하면") {
                then("발급한 refreshToken이 DB에 저장되어 refresh에 쓸 수 있다") {
                    authService.signup(AuthCommand.Signup("rt-login@test.com", "password123", "유저"))

                    val tokens = authService.login(AuthCommand.Login("rt-login@test.com", "password123"))

                    authService.refresh(tokens.refreshToken) shouldNotBe null
                }
            }
        }
    }
}
