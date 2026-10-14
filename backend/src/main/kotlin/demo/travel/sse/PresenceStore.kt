package demo.travel.sse

import demo.travel.user.User
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

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

    fun getUsers(tripId: UUID): List<PresenceUser> =
        store[tripId]?.values?.toList() ?: emptyList()
}

data class PresenceUser(
    val userId: UUID,
    val nickname: String,
    val avatarColor: String,
    val activeDay: Int?,
) {
    companion object {
        private val AVATAR_COLORS = listOf("#7F77DD", "#E8735A", "#3BA889", "#E0A63B", "#4A90D9", "#D45D9A")

        fun of(user: User) = PresenceUser(user.id, user.nickname, avatarColorOf(user.id), activeDay = null)

        /** userId 기반으로 항상 같은 아바타 색을 고른다. 예전 프론트 계산(문자열 해시 31)과 같은 결과다. */
        fun avatarColorOf(userId: UUID): String =
            AVATAR_COLORS[(abs(userId.toString().hashCode().toLong()) % AVATAR_COLORS.size).toInt()]
    }
}
