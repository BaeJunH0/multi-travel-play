package demo.travel.websocket

import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Component
class PresenceStore {
    // tripId → (userId → PresenceUser)
    private val store = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, PresenceUser>>()

    fun join(tripId: UUID, user: PresenceUser) {
        store.getOrPut(tripId) { ConcurrentHashMap() }[user.userId] = user
    }

    fun leave(tripId: UUID, userId: UUID) {
        store[tripId]?.remove(userId)
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
