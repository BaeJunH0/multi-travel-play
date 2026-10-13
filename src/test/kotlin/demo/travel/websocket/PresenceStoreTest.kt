package demo.travel.websocket

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.*

class PresenceStoreTest : BehaviorSpec({

    val tripId = UUID.randomUUID()
    fun presenceUser(userId: UUID = UUID.randomUUID()) = PresenceUser(userId, "유저", "#ff0000", 1)

    given("leaveSession") {
        `when`("유저의 유일한 세션이 끊길 때") {
            then("presence에서 제거하고 tripId를 반환한다") {
                val store = PresenceStore()
                store.join(tripId, presenceUser(), "s1")

                store.leaveSession("s1") shouldBe tripId
                store.getUsers(tripId).shouldBeEmpty()
            }
        }

        `when`("같은 유저의 다른 세션(탭)이 남아 있을 때") {
            then("presence를 유지하고 null을 반환한다") {
                val store = PresenceStore()
                val user = presenceUser()
                store.join(tripId, user, "s1")
                store.join(tripId, user, "s2")

                store.leaveSession("s1") shouldBe null
                store.getUsers(tripId) shouldHaveSize 1

                store.leaveSession("s2") shouldBe tripId
                store.getUsers(tripId).shouldBeEmpty()
            }
        }

        `when`("presence를 발행한 적 없는 세션이 끊길 때") {
            then("아무것도 바꾸지 않고 null을 반환한다") {
                val store = PresenceStore()
                store.join(tripId, presenceUser(), "s1")

                store.leaveSession("unknown") shouldBe null
                store.getUsers(tripId) shouldHaveSize 1
            }
        }

        `when`("같은 여행의 다른 유저가 끊길 때") {
            then("그 유저만 제거한다") {
                val store = PresenceStore()
                val stay = presenceUser()
                store.join(tripId, stay, "s1")
                store.join(tripId, presenceUser(), "s2")

                store.leaveSession("s2") shouldBe tripId
                store.getUsers(tripId) shouldBe listOf(stay)
            }
        }
    }
})
