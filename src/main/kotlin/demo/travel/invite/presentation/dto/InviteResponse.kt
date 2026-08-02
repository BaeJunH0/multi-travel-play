package demo.travel.invite.presentation.dto

import demo.travel.invite.application.dto.InviteResult
import java.time.LocalDate
import java.util.UUID

object InviteResponse {
    data class Link(
        val shareToken: String,
        val inviteUrl: String,
    ) {
        companion object {
            fun of(result: InviteResult.Link) = Link(
                shareToken = result.shareToken,
                inviteUrl = result.inviteUrl,
            )
        }
    }

    data class Info(
        val tripId: UUID,
        val tripTitle: String,
        val destination: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val memberCount: Int,
        val inviterNickname: String,
    ) {
        companion object {
            fun of(result: InviteResult.Info) = Info(
                tripId = result.tripId,
                tripTitle = result.tripTitle,
                destination = result.destination,
                startDate = result.startDate,
                endDate = result.endDate,
                memberCount = result.memberCount,
                inviterNickname = result.inviterNickname,
            )
        }
    }
}
