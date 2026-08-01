package demo.travel.invite.dto

import java.time.LocalDate
import java.util.*

data class InviteLinkResponse(
    val shareToken: String,
    val inviteUrl: String,
)

data class InviteInfoResponse(
    val tripId: UUID,
    val tripTitle: String,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val memberCount: Int,
    val inviterNickname: String,
)
