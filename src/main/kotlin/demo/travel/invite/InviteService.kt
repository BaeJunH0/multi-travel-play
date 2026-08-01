package demo.travel.invite

import demo.travel.trip.TripMember
import demo.travel.trip.TripMemberRepository
import demo.travel.trip.TripRepository
import demo.travel.trip.TripRole
import demo.travel.user.User
import demo.travel.invite.dto.InviteInfoResponse
import demo.travel.invite.dto.InviteLinkResponse
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
@Transactional
class InviteService(
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
) {
    fun createInviteLink(tripId: UUID, userId: UUID, baseUrl: String): InviteLinkResponse {
        val member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
        if (member.role == TripRole.VIEWER) throw ResponseStatusException(HttpStatus.FORBIDDEN)

        val trip = member.trip
        val token = trip.shareToken ?: UUID.randomUUID().toString().replace("-", "").take(16)
        trip.shareToken = token

        return InviteLinkResponse(token, "$baseUrl/invite/$token")
    }

    @Transactional(readOnly = true)
    fun getInviteInfo(shareToken: String): InviteInfoResponse {
        val trip = tripRepository.findByShareToken(shareToken)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val memberCount = tripMemberRepository.countByTripId(trip.id)
        val ownerNickname = tripMemberRepository.findByTripIdAndRole(trip.id, TripRole.OWNER)
            ?.user?.nickname ?: ""

        return InviteInfoResponse(
            tripId = trip.id,
            tripTitle = trip.title,
            destination = trip.destination,
            startDate = trip.startDate,
            endDate = trip.endDate,
            memberCount = memberCount,
            inviterNickname = ownerNickname,
        )
    }

    fun accept(shareToken: String, user: User): UUID {
        val trip = tripRepository.findByShareToken(shareToken)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

        val alreadyMember = tripMemberRepository.findByTripIdAndUserId(trip.id, user.id)
        if (alreadyMember != null) return trip.id

        tripMemberRepository.save(TripMember(trip = trip, user = user, role = TripRole.VIEWER))
        return trip.id
    }
}
