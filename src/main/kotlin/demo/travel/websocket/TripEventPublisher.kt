package demo.travel.websocket

import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import java.util.*

@Component
class TripEventPublisher(private val messaging: SimpMessagingTemplate) {

    fun publish(tripId: UUID, event: TripEvent) {
        messaging.convertAndSend("/topic/trip.$tripId", event)
    }
}
