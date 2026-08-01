package demo.travel.websocket

import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
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
    ) {
        if (request == null) return
        presenceStore.join(tripId, PresenceUser(request.userId, request.nickname, request.avatarColor, request.activeDay))
        val users = presenceStore.getUsers(tripId)
        eventPublisher.publish(tripId, TripEvent(TripEvent.Action.PRESENCE, tripId, mapOf("users" to users)))
    }
}

data class PresenceRequest(
    val userId: UUID,
    val nickname: String,
    val avatarColor: String,
    val activeDay: Int?,
)
