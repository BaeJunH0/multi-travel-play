package demo.travel.sse

import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.*

/** 여행 이벤트 SSE 연결 하나. 같은 유저가 여러 탭으로 접속하면 연결도 여러 개다. */
class TripConnection(
    val tripId: UUID,
    val userId: UUID,
    val emitter: SseEmitter = SseEmitter(0L),  // 시간 제한 없음. 끊김은 heartbeat 전송 실패로 감지한다
) {
    /** 전송에 실패하면 끊긴 연결로 보고 emitter를 종료한 뒤 false를 반환한다. */
    fun send(event: SseEmitter.SseEventBuilder): Boolean =
        try {
            emitter.send(event)
            true
        } catch (e: Exception) {
            emitter.completeWithError(e)
            false
        }
}

/** 서버 메모리에 SSE 연결을 보관한다 (단일 인스턴스 전제). */
@Component
class TripConnectionRegistry {
    private val connections = mutableListOf<TripConnection>()

    @Synchronized
    fun add(connection: TripConnection) {
        connections += connection
    }

    /**
     * 연결을 제거한다. 실제로 제거했고 그 유저의 해당 여행 마지막 연결이었으면 true.
     * 이미 제거된 연결이거나 같은 유저의 다른 연결(탭)이 남아 있으면 false.
     */
    @Synchronized
    fun remove(connection: TripConnection): Boolean {
        if (!connections.remove(connection)) return false
        return connections.none { it.tripId == connection.tripId && it.userId == connection.userId }
    }

    @Synchronized
    fun of(tripId: UUID): List<TripConnection> = connections.filter { it.tripId == tripId }

    @Synchronized
    fun all(): List<TripConnection> = connections.toList()
}
