package demo.travel.block.application

import demo.travel.block.BlockRepository
import demo.travel.block.ScheduleBlock
import demo.travel.block.application.dto.BlockCommand
import demo.travel.block.application.dto.BlockResult
import demo.travel.block.event.BlockDeletedEvent
import demo.travel.block.event.toCostChangedEventOrNull
import demo.travel.common.exception.VersionConflictException
import demo.travel.trip.TripMemberRepository
import demo.travel.trip.TripRole
import demo.travel.user.UserRepository
import org.springframework.context.ApplicationEventPublisher
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
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional(readOnly = true)
    fun getBlocks(tripId: UUID, userId: UUID): List<BlockResult> {
        requireMember(tripId, userId)
        return blockRepository.findAllByTripIdOrderByDayNumberAscPositionAsc(tripId)
            .map { BlockResult.of(it) }
    }

    fun addBlock(command: BlockCommand.Create): BlockResult {
        requireEditorOrAbove(command.tripId, command.userId)
        val lastPosition = blockRepository
            .findTopByTripIdAndDayNumberOrderByPositionDesc(command.tripId, command.dayNumber)
            ?.position ?: 0.0
        val block = blockRepository.save(
            ScheduleBlock(
                trip = tripMemberRepository.findByTripIdAndUserId(command.tripId, command.userId)!!.trip,
                dayNumber = command.dayNumber,
                position = lastPosition + 1.0,
                blockType = command.blockType,
                placeName = command.placeName,
                startTime = command.startTime?.let { LocalTime.parse(it) },
                durationMin = command.durationMin,
                cost = command.cost,
                memo = command.memo,
                createdBy = userRepository.getReferenceById(command.userId),
            )
        )
        block.toCostChangedEventOrNull()?.let { eventPublisher.publishEvent(it) }
        return BlockResult.of(block)
    }

    fun updateBlock(command: BlockCommand.Update): BlockResult {
        requireEditorOrAbove(command.tripId, command.userId)
        val block = findBlockOrThrow(command.blockId, command.tripId)
        checkLock(block, command.userId)
        checkVersion(block, command.version)

        command.placeName?.let { block.placeName = it }
        command.startTime?.let { block.startTime = LocalTime.parse(it) }
        command.durationMin?.let { block.durationMin = it }
        command.cost?.let { block.cost = it }
        command.memo?.let { block.memo = it }

        blockRepository.flush()
        if (command.cost != null) block.toCostChangedEventOrNull()?.let { eventPublisher.publishEvent(it) }
        return BlockResult.of(block)
    }

    fun moveBlock(command: BlockCommand.Move): BlockResult {
        requireEditorOrAbove(command.tripId, command.userId)
        val block = findBlockOrThrow(command.blockId, command.tripId)
        checkVersion(block, command.version)

        block.dayNumber = command.dayNumber
        block.position = command.position

        blockRepository.flush()
        return BlockResult.of(block)
    }

    fun deleteBlock(tripId: UUID, blockId: UUID, userId: UUID) {
        requireEditorOrAbove(tripId, userId)
        val block = findBlockOrThrow(blockId, tripId)
        blockRepository.delete(block)
        eventPublisher.publishEvent(BlockDeletedEvent(block.id))
    }

    fun lockBlock(tripId: UUID, blockId: UUID, userId: UUID): BlockResult {
        requireEditorOrAbove(tripId, userId)
        val block = findBlockOrThrow(blockId, tripId)
        if (block.lockedBy != null && block.lockedBy!!.id != userId) {
            throw ResponseStatusException(HttpStatus.valueOf(423), "다른 사용자가 편집 중입니다.")
        }
        block.lockedBy = userRepository.getReferenceById(userId)
        return BlockResult.of(block)
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
        if (block.version != requestVersion) throw VersionConflictException(BlockResult.of(block))
    }
}
