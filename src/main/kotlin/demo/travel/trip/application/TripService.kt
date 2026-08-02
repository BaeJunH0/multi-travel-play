package demo.travel.trip.application

import demo.travel.trip.Trip
import demo.travel.trip.TripMember
import demo.travel.trip.TripMemberRepository
import demo.travel.trip.TripRepository
import demo.travel.trip.TripRole
import demo.travel.trip.application.dto.TripCommand
import demo.travel.trip.application.dto.TripResult
import demo.travel.user.UserRepository
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
    private val userRepository: UserRepository,
) {
    fun create(command: TripCommand.Create): TripResult.Detail {
        val owner = userRepository.getReferenceById(command.userId)
        val trip = tripRepository.save(
            Trip(
                owner = owner,
                title = command.title,
                destination = command.destination,
                startDate = command.startDate,
                endDate = command.endDate,
            )
        )
        tripMemberRepository.save(TripMember(trip = trip, user = owner, role = TripRole.OWNER))
        return TripResult.Detail.of(trip, TripRole.OWNER, 1)
    }

    @Transactional(readOnly = true)
    fun getList(userId: UUID): List<TripResult.Summary> {
        return tripMemberRepository.findAllWithTripByUserId(userId).map { member ->
            TripResult.Summary.of(member, tripMemberRepository.countByTripId(member.trip.id))
        }
    }

    @Transactional(readOnly = true)
    fun getDetail(tripId: UUID, userId: UUID): TripResult.Detail {
        val trip = findTripOrThrow(tripId)
        val member = findMemberOrThrow(tripId, userId)
        return TripResult.Detail.of(trip, member.role, tripMemberRepository.countByTripId(tripId))
    }

    fun update(command: TripCommand.Update): TripResult.Detail {
        val trip = findTripOrThrow(command.tripId)
        val member = findMemberOrThrow(command.tripId, command.userId)
        requireEditorOrAbove(member)

        command.title?.let { trip.title = it }
        command.destination?.let { trip.destination = it }
        command.startDate?.let { trip.startDate = it }
        command.endDate?.let { trip.endDate = it }

        return TripResult.Detail.of(trip, member.role, tripMemberRepository.countByTripId(command.tripId))
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
