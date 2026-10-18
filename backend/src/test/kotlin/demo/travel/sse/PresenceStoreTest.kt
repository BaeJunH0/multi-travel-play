package demo.travel.sse

import demo.travel.sse.application.PresenceStore
import demo.travel.sse.application.dto.PresenceUser
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.util.*

class PresenceStoreTest : BehaviorSpec({

    val tripId = UUID.randomUUID()
    fun presenceUser(userId: UUID = UUID.randomUUID(), activeDay: Int? = null) = PresenceUser(userId, "유저", "#ff0000", activeDay)

    given("join") {
        `when`("이미 접속 중인 유저가 다른 탭으로 다시 접속할 때") {
            then("기존 정보(activeDay)를 유지한다") {
                val store = PresenceStore()
                val userId = UUID.randomUUID()
                store.join(tripId, presenceUser(userId, activeDay = 2))

                store.join(tripId, presenceUser(userId, activeDay = null))

                store.getUsers(tripId).single().activeDay shouldBe 2
            }
        }
    }

    given("updateActiveDay") {
        `when`("접속 중인 유저일 때") {
            then("activeDay를 바꾸고 true를 반환한다") {
                val store = PresenceStore()
                val user = presenceUser()
                store.join(tripId, user)

                store.updateActiveDay(tripId, user.userId, 3) shouldBe true
                store.getUsers(tripId) shouldBe listOf(user.copy(activeDay = 3))
            }
        }

        `when`("접속 중이 아닌 유저일 때") {
            then("아무것도 추가하지 않고 false를 반환한다") {
                val store = PresenceStore()

                store.updateActiveDay(tripId, UUID.randomUUID(), 3) shouldBe false
                store.getUsers(tripId).shouldBeEmpty()
            }
        }
    }

    given("leave") {
        `when`("같은 여행의 한 유저가 나갈 때") {
            then("그 유저만 제거한다") {
                val store = PresenceStore()
                val stay = presenceUser()
                val gone = presenceUser()
                store.join(tripId, stay)
                store.join(tripId, gone)

                store.leave(tripId, gone.userId)

                store.getUsers(tripId) shouldBe listOf(stay)
            }
        }
    }

    given("PresenceUser.avatarColorOf") {
        `when`("userId가 주어지면") {
            then("예전 프론트 계산(문자열 해시)과 같은 색을 고른다") {
                // 기대값은 예전 프론트 avatarColorOf로 계산한 값 (해시가 음수인 경우 포함)
                PresenceUser.avatarColorOf(UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301")) shouldBe "#D45D9A"
                PresenceUser.avatarColorOf(UUID.fromString("00000000-0000-0000-0000-000000000000")) shouldBe "#7F77DD"
                PresenceUser.avatarColorOf(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")) shouldBe "#3BA889"
            }
        }
    }
})
