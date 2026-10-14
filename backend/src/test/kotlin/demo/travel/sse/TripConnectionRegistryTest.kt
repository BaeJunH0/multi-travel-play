package demo.travel.sse

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.*

class TripConnectionRegistryTest : BehaviorSpec({

    val tripId = UUID.randomUUID()
    val userId = UUID.randomUUID()

    given("remove") {
        `when`("유저의 유일한 연결을 제거할 때") {
            then("마지막 연결이었으므로 true") {
                val registry = TripConnectionRegistry()
                val connection = TripConnection(tripId, userId)
                registry.add(connection)

                registry.remove(connection) shouldBe true
                registry.of(tripId) shouldBe emptyList()
            }
        }

        `when`("같은 유저의 다른 연결(탭)이 남아 있을 때") {
            then("false, 남은 연결까지 제거하면 true") {
                val registry = TripConnectionRegistry()
                val tab1 = TripConnection(tripId, userId)
                val tab2 = TripConnection(tripId, userId)
                registry.add(tab1)
                registry.add(tab2)

                registry.remove(tab1) shouldBe false
                registry.remove(tab2) shouldBe true
            }
        }

        `when`("같은 유저가 다른 여행에만 연결이 남아 있을 때") {
            then("이 여행의 마지막 연결이므로 true") {
                val registry = TripConnectionRegistry()
                val connection = TripConnection(tripId, userId)
                registry.add(connection)
                registry.add(TripConnection(UUID.randomUUID(), userId))

                registry.remove(connection) shouldBe true
            }
        }

        `when`("이미 제거된 연결을 다시 제거할 때") {
            then("false (연결 종료 콜백이 여러 번 와도 한 번만 처리된다)") {
                val registry = TripConnectionRegistry()
                val connection = TripConnection(tripId, userId)
                registry.add(connection)
                registry.remove(connection)

                registry.remove(connection) shouldBe false
            }
        }
    }

    given("of") {
        `when`("여러 여행의 연결이 섞여 있을 때") {
            then("해당 여행의 연결만 반환한다") {
                val registry = TripConnectionRegistry()
                val mine = TripConnection(tripId, userId)
                registry.add(mine)
                registry.add(TripConnection(UUID.randomUUID(), userId))

                registry.of(tripId) shouldBe listOf(mine)
            }
        }
    }

    given("TripConnection.send") {
        `when`("전송에 실패할 때") {
            then("emitter를 에러로 종료하고 false를 반환한다") {
                val error = IOException("broken pipe")
                val emitter = mockk<SseEmitter>(relaxed = true) {
                    every { send(any<SseEmitter.SseEventBuilder>()) } throws error
                }

                TripConnection(tripId, userId, emitter).send(SseEmitter.event().comment("heartbeat")) shouldBe false
                verify { emitter.completeWithError(error) }
            }
        }
    }
})
