package demo.travel.user

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class UserRepositoryTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var userRepository: UserRepository

    init {
        given("findByEmail") {
            `when`("해당 이메일의 사용자가 존재할 때") {
                then("User를 반환한다") {
                    userRepository.save(User(email = "ur-exists@test.com", nickname = "존재유저", provider = AuthProvider.LOCAL))

                    val result = userRepository.findByEmail("ur-exists@test.com")

                    result shouldNotBe null
                    result!!.email shouldBe "ur-exists@test.com"
                    result.nickname shouldBe "존재유저"
                    result.provider shouldBe AuthProvider.LOCAL
                }
            }

            `when`("해당 이메일의 사용자가 없을 때") {
                then("null을 반환한다") {
                    val result = userRepository.findByEmail("ur-ghost@test.com")

                    result shouldBe null
                }
            }

            `when`("소셜 로그인 사용자일 때") {
                then("provider와 password를 함께 반환한다") {
                    userRepository.save(User(email = "ur-kakao@kakao.local", nickname = "카카오유저", provider = AuthProvider.KAKAO))

                    val result = userRepository.findByEmail("ur-kakao@kakao.local")

                    result shouldNotBe null
                    result!!.provider shouldBe AuthProvider.KAKAO
                    result.password shouldBe null
                }
            }
        }

        given("existsByEmail") {
            `when`("해당 이메일의 사용자가 존재할 때") {
                then("true를 반환한다") {
                    userRepository.save(User(email = "ur-present@test.com", nickname = "유저", provider = AuthProvider.LOCAL))

                    val result = userRepository.existsByEmail("ur-present@test.com")

                    result shouldBe true
                }
            }

            `when`("해당 이메일의 사용자가 없을 때") {
                then("false를 반환한다") {
                    val result = userRepository.existsByEmail("ur-absent@test.com")

                    result shouldBe false
                }
            }
        }
    }
}
