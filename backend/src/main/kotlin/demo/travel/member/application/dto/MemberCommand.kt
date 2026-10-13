package demo.travel.member.application.dto

import demo.travel.trip.TripRole
import java.util.UUID

object MemberCommand {
    data class UpdateRole(
        val tripId: UUID,
        val targetUserId: UUID,
        val role: TripRole,
        val requesterId: UUID,
    )
}
