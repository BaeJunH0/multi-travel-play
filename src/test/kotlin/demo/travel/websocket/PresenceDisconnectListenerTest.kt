package demo.travel.websocket

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.*
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import java.util.*

class PresenceDisconnectListenerTest : BehaviorSpec({

    val eventPublisher = mockk<TripEventPublisher>(relaxed = true)
    val tripId = UUID.randomUUID()
    fun disconnectOf(sessionId: String) = mockk<SessionDisconnectEvent> { every { this@mockk.sessionId } returns sessionId }

    beforeEach { clearAllMocks() }

    given("onDisconnect") {
        `when`("presence에 등록된 유저의 마지막 세션이 끊길 때") {
            then("남은 접속자 목록으로 PRESENCE를 브로드캐스트한다") {
                val store = PresenceStore()
                val stay = PresenceUser(UUID.randomUUID(), "남는유저", "#00ff00", 1)
                store.join(tripId, stay, "s1")
                store.join(tripId, PresenceUser(UUID.randomUUID(), "나가는유저", "#ff0000", 2), "s2")
                val listener = PresenceDisconnectListener(store, eventPublisher)

                listener.onDisconnect(disconnectOf("s2"))

                verify(exactly = 1) {
                    eventPublisher.publish(tripId, match {
                        it.action == TripEvent.Action.PRESENCE && it.payload == mapOf("users" to listOf(stay))
                    })
                }
            }
        }

        `when`("presence를 발행한 적 없는 세션이 끊길 때") {
            then("아무것도 발행하지 않는다") {
                val listener = PresenceDisconnectListener(PresenceStore(), eventPublisher)

                listener.onDisconnect(disconnectOf("unknown"))

                verify(exactly = 0) { eventPublisher.publish(any(), any()) }
            }
        }
    }
})
