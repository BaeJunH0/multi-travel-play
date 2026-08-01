package demo.travel.user

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class UserTest : BehaviorSpec({

    given("User.changePassword") {
        `when`("인코딩된 비밀번호를 전달하면") {
            then("password 필드가 교체된다") {
                val user = User(email = "user@test.com", nickname = "유저", provider = AuthProvider.LOCAL)

                user.changePassword("encoded-pw-hash")

                user.password shouldBe "encoded-pw-hash"
            }
        }

        `when`("최초 비밀번호가 없는 소셜 유저에게 호출하면") {
            then("password 필드가 새로 설정된다") {
                val user = User(email = "kakao@kakao.local", nickname = "카카오유저", provider = AuthProvider.KAKAO, password = null)

                user.changePassword("new-encoded-pw")

                user.password shouldNotBe null
                user.password shouldBe "new-encoded-pw"
            }
        }

        `when`("기존 비밀번호가 있을 때 다시 호출하면") {
            then("이전 값을 덮어쓴다") {
                val user = User(email = "local@test.com", nickname = "유저", provider = AuthProvider.LOCAL, password = "old-hash")

                user.changePassword("new-hash")

                user.password shouldBe "new-hash"
            }
        }
    }
})
