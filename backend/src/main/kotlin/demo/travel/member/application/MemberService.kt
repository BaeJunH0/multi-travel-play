package demo.travel.member.application

import demo.travel.member.application.dto.MemberCommand
import demo.travel.member.application.dto.MemberResult
import demo.travel.trip.TripMemberRepository
import demo.travel.trip.TripRole
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
@Transactional
class MemberService(private val tripMemberRepository: TripMemberRepository) {

    @Transactional(readOnly = true)
    fun getMembers(tripId: UUID, requesterId: UUID): List<MemberResult> {
        tripMemberRepository.findByTripIdAndUserId(tripId, requesterId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
        return tripMemberRepository.findAllByTripId(tripId).map { MemberResult.of(it) }
    }

    fun updateRole(command: MemberCommand.UpdateRole) {
        val requester = tripMemberRepository.findByTripIdAndUserId(command.tripId, command.requesterId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
        if (requester.role != TripRole.OWNER) throw ResponseStatusException(HttpStatus.FORBIDDEN)
        if (command.role == TripRole.OWNER) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "OWNER 권한은 직접 양도할 수 없습니다.")

        val target = tripMemberRepository.findByTripIdAndUserId(command.tripId, command.targetUserId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (target.role == TripRole.OWNER) throw ResponseStatusException(HttpStatus.FORBIDDEN, "OWNER 역할은 변경할 수 없습니다.")

        target.role = command.role
    }

    fun removeMember(tripId: UUID, targetUserId: UUID, requesterId: UUID) {
        val requester = tripMemberRepository.findByTripIdAndUserId(tripId, requesterId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
        if (requester.role != TripRole.OWNER) throw ResponseStatusException(HttpStatus.FORBIDDEN)

        val target = tripMemberRepository.findByTripIdAndUserId(tripId, targetUserId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (target.role == TripRole.OWNER) throw ResponseStatusException(HttpStatus.FORBIDDEN, "OWNER는 제거할 수 없습니다.")

        tripMemberRepository.delete(target)
    }
}
