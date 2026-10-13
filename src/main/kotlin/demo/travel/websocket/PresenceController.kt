package demo.travel.websocket

import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller
import java.util.*

@Controller
class PresenceController(
    private val presenceStore: PresenceStore,
    private val eventPublisher: TripEventPublisher,
) {
    @MessageMapping("trip.{tripId}.presence")
    fun presence(
        @DestinationVariable tripId: UUID,
        @Payload(required = false) request: PresenceRequest?,
        @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) sessionId: String,
    ) {
        if (request == null) return
        presenceStore.join(
            tripId,
            PresenceUser(request.userId, request.nickname, request.avatarColor, request.activeDay),
            sessionId,
        )
        eventPublisher.publish(tripId, TripEvent.presence(tripId, presenceStore.getUsers(tripId)))
    }
}

data class PresenceRequest(
    val userId: UUID,
    val nickname: String,
    val avatarColor: String,
    val activeDay: Int?,
)
