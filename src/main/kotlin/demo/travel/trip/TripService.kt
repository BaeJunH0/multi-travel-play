package demo.travel.trip

import demo.travel.user.User
import demo.travel.trip.dto.TripDetailResponse
import demo.travel.trip.dto.TripRequest
import demo.travel.trip.dto.TripSummaryResponse
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
@Transactional
class TripService(
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
) {
    fun create(request: TripRequest.Create, user: User): TripDetailResponse {
        val trip = tripRepository.save(
            Trip(
                owner = user,
                title = request.title,
                destination = request.destination,
                startDate = request.startDate,
                endDate = request.endDate,
            )
        )
        tripMemberRepository.save(TripMember(trip = trip, user = user, role = TripRole.OWNER))
        return TripDetailResponse.of(trip, TripRole.OWNER, 1)
    }

    @Transactional(readOnly = true)
    fun getList(userId: UUID): List<TripSummaryResponse> {
        return tripMemberRepository.findAllWithTripByUserId(userId).map { member ->
            TripSummaryResponse.of(member, tripMemberRepository.countByTripId(member.trip.id))
        }
    }

    @Transactional(readOnly = true)
    fun getDetail(tripId: UUID, userId: UUID): TripDetailResponse {
        val trip = findTripOrThrow(tripId)
        val member = findMemberOrThrow(tripId, userId)
        return TripDetailResponse.of(trip, member.role, tripMemberRepository.countByTripId(tripId))
    }

    fun update(tripId: UUID, request: TripRequest.Update, userId: UUID): TripDetailResponse {
        val trip = findTripOrThrow(tripId)
        val member = findMemberOrThrow(tripId, userId)
        requireEditorOrAbove(member)

        request.title?.let { trip.title = it }
        request.destination?.let { trip.destination = it }
        request.startDate?.let { trip.startDate = it }
        request.endDate?.let { trip.endDate = it }

        return TripDetailResponse.of(trip, member.role, tripMemberRepository.countByTripId(tripId))
    }

    fun delete(tripId: UUID, userId: UUID) {
        val member = findMemberOrThrow(tripId, userId)
        if (member.role != TripRole.OWNER) throw ResponseStatusException(HttpStatus.FORBIDDEN)
        tripRepository.deleteById(tripId)
    }

    private fun findTripOrThrow(tripId: UUID) =
        tripRepository.findByIdOrNull(tripId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    private fun findMemberOrThrow(tripId: UUID, userId: UUID) =
        tripMemberRepository.findByTripIdAndUserId(tripId, userId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)

    private fun requireEditorOrAbove(member: TripMember) {
        if (member.role == TripRole.VIEWER) throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }
}
