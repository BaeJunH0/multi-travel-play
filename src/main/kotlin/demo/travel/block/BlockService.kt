package demo.travel.block

import demo.travel.block.dto.BlockRequest
import demo.travel.block.dto.BlockResponse
import demo.travel.common.exception.VersionConflictException
import demo.travel.trip.TripMemberRepository
import demo.travel.trip.TripRole
import demo.travel.user.User
import demo.travel.user.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalTime
import java.util.*

@Service
@Transactional
class BlockService(
    private val blockRepository: BlockRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun getBlocks(tripId: UUID, userId: UUID): List<BlockResponse> {
        requireMember(tripId, userId)
        return blockRepository.findAllByTripIdOrderByDayNumberAscPositionAsc(tripId)
            .map { BlockResponse.of(it) }
    }

    fun addBlock(tripId: UUID, request: BlockRequest.Create, user: User): BlockResponse {
        requireEditorOrAbove(tripId, user.id)
        val lastPosition = blockRepository
            .findTopByTripIdAndDayNumberOrderByPositionDesc(tripId, request.dayNumber)
            ?.position ?: 0.0
        val block = blockRepository.save(
            ScheduleBlock(
                trip = tripMemberRepository.findByTripIdAndUserId(tripId, user.id)!!.trip,
                dayNumber = request.dayNumber,
                position = lastPosition + 1.0,
                blockType = request.blockType,
                placeName = request.placeName,
                startTime = request.startTime?.let { LocalTime.parse(it) },
                durationMin = request.durationMin,
                cost = request.cost,
                memo = request.memo,
                createdBy = user,
            )
        )
        return BlockResponse.of(block)
    }

    fun updateBlock(tripId: UUID, blockId: UUID, request: BlockRequest.Update, userId: UUID): BlockResponse {
        requireEditorOrAbove(tripId, userId)
        val block = findBlockOrThrow(blockId, tripId)
        checkLock(block, userId)
        checkVersion(block, request.version)

        request.placeName?.let { block.placeName = it }
        request.startTime?.let { block.startTime = LocalTime.parse(it) }
        request.durationMin?.let { block.durationMin = it }
        request.cost?.let { block.cost = it }
        request.memo?.let { block.memo = it }

        blockRepository.flush()
        return BlockResponse.of(block)
    }

    fun moveBlock(tripId: UUID, blockId: UUID, request: BlockRequest.Move, userId: UUID): BlockResponse {
        requireEditorOrAbove(tripId, userId)
        val block = findBlockOrThrow(blockId, tripId)
        checkVersion(block, request.version)

        block.dayNumber = request.dayNumber
        block.position = request.position

        blockRepository.flush()
        return BlockResponse.of(block)
    }

    fun deleteBlock(tripId: UUID, blockId: UUID, userId: UUID) {
        requireEditorOrAbove(tripId, userId)
        val block = findBlockOrThrow(blockId, tripId)
        blockRepository.delete(block)
    }

    fun lockBlock(tripId: UUID, blockId: UUID, userId: UUID): BlockResponse {
        requireEditorOrAbove(tripId, userId)
        val block = findBlockOrThrow(blockId, tripId)
        if (block.lockedBy != null && block.lockedBy!!.id != userId) {
            throw ResponseStatusException(HttpStatus.valueOf(423), "다른 사용자가 편집 중입니다.")
        }
        block.lockedBy = userRepository.getReferenceById(userId)
        return BlockResponse.of(block)
    }

    fun unlockBlock(tripId: UUID, blockId: UUID, userId: UUID) {
        val block = findBlockOrThrow(blockId, tripId)
        if (block.lockedBy?.id == userId) block.lockedBy = null
    }

    fun reorderBlocks(tripId: UUID, userId: UUID) {
        requireEditorOrAbove(tripId, userId)
        val blocks = blockRepository.findAllByTripIdOrderByDayNumberAscPositionAsc(tripId)
        val grouped = blocks.groupBy { it.dayNumber }
        grouped.values.forEach { dayBlocks ->
            dayBlocks.forEachIndexed { i, block -> block.position = (i + 1).toDouble() }
        }
    }

    private fun findBlockOrThrow(blockId: UUID, tripId: UUID): ScheduleBlock {
        val block = blockRepository.findByIdOrNull(blockId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (block.trip.id != tripId) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return block
    }

    private fun requireMember(tripId: UUID, userId: UUID) {
        tripMemberRepository.findByTripIdAndUserId(tripId, userId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    private fun requireEditorOrAbove(tripId: UUID, userId: UUID) {
        val member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
        if (member.role == TripRole.VIEWER) throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    private fun checkLock(block: ScheduleBlock, userId: UUID) {
        if (block.lockedBy != null && block.lockedBy!!.id != userId) {
            throw ResponseStatusException(HttpStatus.valueOf(423), "다른 사용자가 편집 중입니다.")
        }
    }

    private fun checkVersion(block: ScheduleBlock, requestVersion: Long) {
        if (block.version != requestVersion) throw VersionConflictException(BlockResponse.of(block))
    }
}
