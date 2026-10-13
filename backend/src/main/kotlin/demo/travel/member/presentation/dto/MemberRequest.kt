package demo.travel.member.presentation.dto

import demo.travel.trip.TripRole

object MemberRequest {
    data class UpdateRole(val role: TripRole)
}
