package demo.travel.sse.application

import demo.travel.sse.application.dto.TripEvent
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.*

@Component
class TripEventPublisher(private val connections: TripConnectionRegistry) {

    /** 해당 여행에 SSE로 연결된 모든 접속자에게 이벤트를 보낸다. */
    fun publish(tripId: UUID, event: TripEvent) {
        connections.of(tripId).forEach { it.send(SseEmitter.event().data(event)) }
    }
}
