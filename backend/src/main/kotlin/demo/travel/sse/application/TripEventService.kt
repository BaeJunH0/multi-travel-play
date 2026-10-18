package demo.travel.sse.application

import demo.travel.block.application.BlockService
import demo.travel.sse.application.dto.PresenceUser
import demo.travel.sse.application.dto.TripEvent
import demo.travel.trip.TripMemberRepository
import demo.travel.user.User
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.*

/** SSE 연결 수명 관리: 연결 시 presence 등록, 주기적 heartbeat, 끊기면 presence 제거와 잠금 해제. */
@Service
class TripEventService(
    private val connections: TripConnectionRegistry,
    private val presenceStore: PresenceStore,
    private val eventPublisher: TripEventPublisher,
    private val blockService: BlockService,
    private val tripMemberRepository: TripMemberRepository,
) {
    fun connect(tripId: UUID, user: User): SseEmitter {
        tripMemberRepository.findByTripIdAndUserId(tripId, user.id)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)

        val connection = TripConnection(tripId, user.id)
        connection.emitter.onCompletion { disconnect(connection) }
        connection.emitter.onError { disconnect(connection) }
        connections.add(connection)

        presenceStore.join(tripId, PresenceUser.of(user))
        publishPresence(tripId)
        return connection.emitter
    }

    fun updatePresence(tripId: UUID, userId: UUID, activeDay: Int?) {
        if (presenceStore.updateActiveDay(tripId, userId, activeDay)) publishPresence(tripId)
    }

    /**
     * 모든 연결에 heartbeat(SSE 주석)를 보내 끊긴 연결을 정리한다.
     * 그 뒤 연결이 살아 있는 유저의 잠금은 만료를 연장하고, 만료된 잠금은 해제해 UNLOCK을 알린다.
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    fun heartbeat() {
        connections.all()
            .filterNot { it.send(SseEmitter.event().comment("heartbeat")) }
            .forEach { disconnect(it) }

        val connected = connections.all().map { it.tripId to it.userId }.toSet()
        blockService.refreshLocks(connected).forEach { (tripId, blockId) ->
            eventPublisher.publish(tripId, TripEvent.unlock(tripId, blockId))
        }
    }

    /** 연결 종료 처리. 그 유저의 해당 여행 마지막 연결이면 presence에서 빼고 잡고 있던 잠금을 모두 해제한다. */
    fun disconnect(connection: TripConnection) {
        if (!connections.remove(connection)) return
        val tripId = connection.tripId

        presenceStore.leave(tripId, connection.userId)
        publishPresence(tripId)
        blockService.releaseLocks(tripId, connection.userId).forEach { blockId ->
            eventPublisher.publish(tripId, TripEvent.unlock(tripId, blockId))
        }
    }

    private fun publishPresence(tripId: UUID) {
        eventPublisher.publish(tripId, TripEvent.presence(tripId, presenceStore.getUsers(tripId)))
    }

    companion object {
        const val HEARTBEAT_INTERVAL_MS = 30_000L
    }
}
