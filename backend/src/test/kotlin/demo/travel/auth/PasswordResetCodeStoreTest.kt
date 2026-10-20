package demo.travel.auth

import demo.travel.auth.application.PasswordResetCodeStore
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class PasswordResetCodeStoreTest :
    BehaviorSpec({

        val email = "user@test.com"
        val start = Instant.parse("2026-10-14T00:00:00Z")

        // 테스트에서 시간을 직접 움직이기 위한 Clock
        class MutableClock(var now: Instant) : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId) = this
            override fun instant() = now
        }

        given("find") {
            `when`("저장 후 15분이 지나기 전일 때") {
                then("저장된 코드를 반환한다") {
                    val clock = MutableClock(start)
                    val store = PasswordResetCodeStore(clock)
                    store.save(email, "123456")

                    clock.now = start.plus(Duration.ofMinutes(14))

                    store.find(email) shouldBe "123456"
                }
            }

            `when`("저장 후 15분이 지났을 때") {
                then("null을 반환한다") {
                    val clock = MutableClock(start)
                    val store = PasswordResetCodeStore(clock)
                    store.save(email, "123456")

                    clock.now = start.plus(Duration.ofMinutes(15))

                    store.find(email) shouldBe null
                }
            }

            `when`("같은 이메일로 다시 저장했을 때") {
                then("마지막 코드만 유효하다") {
                    val store = PasswordResetCodeStore(MutableClock(start))
                    store.save(email, "111111")
                    store.save(email, "222222")

                    store.find(email) shouldBe "222222"
                }
            }

            `when`("저장한 적 없는 이메일일 때") {
                then("null을 반환한다") {
                    PasswordResetCodeStore(MutableClock(start)).find(email) shouldBe null
                }
            }
        }

        given("remove") {
            `when`("저장된 코드를 삭제하면") {
                then("이후 find는 null을 반환한다") {
                    val store = PasswordResetCodeStore(MutableClock(start))
                    store.save(email, "123456")

                    store.remove(email)

                    store.find(email) shouldBe null
                }
            }
        }
    })
