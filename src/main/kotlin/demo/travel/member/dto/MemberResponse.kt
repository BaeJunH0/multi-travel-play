package demo.travel.member.dto

import demo.travel.trip.TripMember
import demo.travel.trip.TripRole
import java.util.UUID

data class MemberResponse(
    val userId: UUID,
    val nickname: String,
    val role: TripRole,
) {
    companion object {
        fun of(member: TripMember) = MemberResponse(
            userId = member.user.id,
            nickname = member.user.nickname,
            role = member.role,
        )
    }
}

data class UpdateRoleRequest(val role: TripRole)
