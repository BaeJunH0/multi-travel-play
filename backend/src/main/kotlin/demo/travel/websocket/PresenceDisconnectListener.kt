package demo.travel.websocket

import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionDisconnectEvent

@Component
class PresenceDisconnectListener(
    private val presenceStore: PresenceStore,
    private val eventPublisher: TripEventPublisher,
) {
    /** WebSocket 연결이 끊기면(탭 닫기·네트워크 끊김 포함) 접속자에서 빼고 남은 목록을 다시 알린다. */
    @EventListener
    fun onDisconnect(event: SessionDisconnectEvent) {
        val tripId = presenceStore.leaveSession(event.sessionId) ?: return
        eventPublisher.publish(tripId, TripEvent.presence(tripId, presenceStore.getUsers(tripId)))
    }
}
