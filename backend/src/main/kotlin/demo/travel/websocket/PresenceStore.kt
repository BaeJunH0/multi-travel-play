package demo.travel.websocket

import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Component
class PresenceStore {
    // tripId → (userId → PresenceUser)
    private val store = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, PresenceUser>>()

    // STOMP sessionId → (tripId, userId). 같은 유저가 여러 탭(세션)으로 접속할 수 있다.
    private val sessions = ConcurrentHashMap<String, Pair<UUID, UUID>>()

    @Synchronized
    fun join(tripId: UUID, user: PresenceUser, sessionId: String) {
        store.getOrPut(tripId) { ConcurrentHashMap() }[user.userId] = user
        sessions[sessionId] = tripId to user.userId
    }

    fun leave(tripId: UUID, userId: UUID) {
        store[tripId]?.remove(userId)
    }

    /**
     * 끊긴 세션을 정리한다. 그 유저의 마지막 세션이었으면 presence에서 제거하고 tripId를 반환한다.
     * presence에 변화가 없으면(모르는 세션이거나 다른 세션이 남아 있으면) null.
     */
    @Synchronized
    fun leaveSession(sessionId: String): UUID? {
        val key = sessions.remove(sessionId) ?: return null
        if (key in sessions.values) return null
        leave(key.first, key.second)
        return key.first
    }

    fun getUsers(tripId: UUID): List<PresenceUser> =
        store[tripId]?.values?.toList() ?: emptyList()
}

data class PresenceUser(
    val userId: UUID,
    val nickname: String,
    val avatarColor: String,
    val activeDay: Int?,
)
