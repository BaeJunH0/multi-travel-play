package demo.travel.member.presentation.dto

import demo.travel.member.application.dto.MemberResult
import demo.travel.trip.TripRole
import java.util.UUID

data class MemberResponse(
    val userId: UUID,
    val nickname: String,
    val role: TripRole,
) {
    companion object {
        fun of(result: MemberResult) = MemberResponse(
            userId = result.userId,
            nickname = result.nickname,
            role = result.role,
        )
    }
}
