package demo.travel.member.application.dto

import demo.travel.trip.TripMember
import demo.travel.trip.TripRole
import java.util.UUID

data class MemberResult(
    val userId: UUID,
    val nickname: String,
    val role: TripRole,
) {
    companion object {
        fun of(member: TripMember) = MemberResult(
            userId = member.user.id,
            nickname = member.user.nickname,
            role = member.role,
        )
    }
}
