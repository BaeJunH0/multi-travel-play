package demo.travel.invite.application.dto

import java.time.LocalDate
import java.util.UUID

object InviteResult {
    data class Link(
        val shareToken: String,
        val inviteUrl: String,
    )

    data class Info(
        val tripId: UUID,
        val tripTitle: String,
        val destination: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val memberCount: Int,
        val inviterNickname: String,
    )
}
