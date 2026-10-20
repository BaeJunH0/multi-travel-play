package demo.travel.sse.application

import demo.travel.sse.application.dto.PresenceUser
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Component
class PresenceStore {
    // tripId → (userId → PresenceUser)
    private val store = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, PresenceUser>>()

    /** 이미 접속 중인 유저(다른 탭)면 기존 정보를 유지한다. */
    fun join(tripId: UUID, user: PresenceUser) {
        store.getOrPut(tripId) { ConcurrentHashMap() }.putIfAbsent(user.userId, user)
    }

    /** 접속 중인 유저의 활성 Day를 바꾼다. 접속 중이 아니면 아무것도 하지 않고 false. */
    fun updateActiveDay(tripId: UUID, userId: UUID, activeDay: Int?): Boolean =
        store[tripId]?.computeIfPresent(userId) { _, user -> user.copy(activeDay = activeDay) } != null

    fun leave(tripId: UUID, userId: UUID) {
        store[tripId]?.remove(userId)
    }

    fun getUsers(tripId: UUID): List<PresenceUser> = store[tripId]?.values?.toList() ?: emptyList()
}
