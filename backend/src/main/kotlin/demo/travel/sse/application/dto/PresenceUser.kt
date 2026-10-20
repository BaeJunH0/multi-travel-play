package demo.travel.sse.application.dto

import demo.travel.user.User
import java.util.*
import kotlin.math.abs

data class PresenceUser(val userId: UUID, val nickname: String, val avatarColor: String, val activeDay: Int?) {
    companion object {
        private val AVATAR_COLORS = listOf("#7F77DD", "#E8735A", "#3BA889", "#E0A63B", "#4A90D9", "#D45D9A")

        fun of(user: User) = PresenceUser(user.id, user.nickname, avatarColorOf(user.id), activeDay = null)

        /** userId 기반으로 항상 같은 아바타 색을 고른다. 예전 프론트 계산(문자열 해시 31)과 같은 결과다. */
        fun avatarColorOf(userId: UUID): String = AVATAR_COLORS[(abs(userId.toString().hashCode().toLong()) % AVATAR_COLORS.size).toInt()]
    }
}
