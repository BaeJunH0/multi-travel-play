package demo.travel.block.application

import demo.travel.block.BlockRepository
import demo.travel.block.ScheduleBlock
import demo.travel.block.application.dto.BlockCommand
import demo.travel.block.application.dto.BlockResult
import demo.travel.block.event.BlockDeletedEvent
import demo.travel.block.event.toCostChangedEventOrNull
import demo.travel.common.TripCategory
import demo.travel.trip.TripMemberRepository
import demo.travel.trip.TripRole
import demo.travel.user.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

@Service
@Transactional
class BlockService(
    private val blockRepository: BlockRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    @Transactional(readOnly = true)
    fun getBlocks(tripId: UUID, userId: UUID): List<BlockResult> {
        requireMember(tripId, userId)
        return blockRepository.findAllByTripIdOrderByDayNumberAscPositionAsc(tripId)
            .map { BlockResult.of(it) }
    }

    fun addBlock(command: BlockCommand.Create): BlockResult {
        requireEditorOrAbove(command.tripId, command.userId)
        requireBlockType(command.blockType)
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
            ),
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
        checkLock(block, command.userId)
        checkVersion(block, command.version)

        block.dayNumber = command.dayNumber
        block.position = command.position

        blockRepository.flush()
        return BlockResult.of(block)
    }

    fun deleteBlock(tripId: UUID, blockId: UUID, userId: UUID) {
        requireEditorOrAbove(tripId, userId)
        val block = findBlockOrThrow(blockId, tripId)
        checkLock(block, userId)
        blockRepository.delete(block)
        eventPublisher.publishEvent(BlockDeletedEvent(block.id))
    }

    fun lockBlock(tripId: UUID, blockId: UUID, userId: UUID): BlockResult {
        requireEditorOrAbove(tripId, userId)
        val block = findBlockOrThrow(blockId, tripId)
        checkLock(block, userId)
        block.lockedBy = userRepository.getReferenceById(userId)
        block.lockExpiresAt = now().plus(LOCK_TTL)
        return BlockResult.of(block)
    }

    fun unlockBlock(tripId: UUID, blockId: UUID, userId: UUID) {
        val block = findBlockOrThrow(blockId, tripId)
        if (block.lockedBy?.id == userId) release(block)
    }

    /** 유저가 해당 여행에서 잡고 있던 잠금을 모두 해제하고, 해제한 blockId 목록을 반환한다. (마지막 SSE 연결 종료 시) */
    fun releaseLocks(tripId: UUID, userId: UUID): List<UUID> = blockRepository.findAllByTripIdAndLockedById(tripId, userId)
        .map { it.id }
        .filter { blockRepository.releaseLock(it, userId) == 1 }

    /**
     * heartbeat마다 호출한다. [connected]는 SSE 연결이 살아 있는 (tripId, userId) 목록.
     * 연결된 잠금자의 잠금은 만료를 연장하고, 그 외 만료된 잠금은 해제한다.
     * 해제한 잠금의 (tripId, blockId) 목록을 반환한다.
     * 읽은 뒤 바뀐 블록(다른 사용자가 새로 잠금 등)은 UPDATE 조건에서 걸러진다.
     */
    fun refreshLocks(connected: Set<Pair<UUID, UUID>>): List<Pair<UUID, UUID>> {
        val now = now()
        val released = mutableListOf<Pair<UUID, UUID>>()
        blockRepository.findAllByLockedByIsNotNull().forEach { block ->
            val lockedById = block.lockedBy!!.id
            if ((block.trip.id to lockedById) in connected) {
                blockRepository.extendLock(block.id, lockedById, now.plus(LOCK_TTL))
            } else if (isLockExpired(block) && blockRepository.releaseExpiredLock(block.id, now) == 1) {
                released += block.trip.id to block.id
            }
        }
        return released
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

    private fun requireBlockType(blockType: TripCategory) {
        if (!blockType.isBlockType) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "블록에 사용할 수 없는 카테고리입니다: $blockType")
        }
    }

    /** 다른 사용자의 잠금이 살아 있으면 423. 만료된 잠금은 없는 것으로 취급한다. */
    private fun checkLock(block: ScheduleBlock, userId: UUID) {
        val lockedBy = block.lockedBy ?: return
        if (lockedBy.id != userId && !isLockExpired(block)) {
            throw ResponseStatusException(HttpStatus.valueOf(423), "다른 사용자가 편집 중입니다.")
        }
    }

    private fun isLockExpired(block: ScheduleBlock) = block.lockExpiresAt?.isAfter(now()) != true

    private fun release(block: ScheduleBlock) {
        block.lockedBy = null
        block.lockExpiresAt = null
    }

    private fun now() = LocalDateTime.now(clock)

    private fun checkVersion(block: ScheduleBlock, requestVersion: Long) {
        if (block.version != requestVersion) throw VersionConflictException(BlockResult.of(block))
    }

    companion object {
        /** 잠금 안전망 TTL. 연결이 살아 있으면 heartbeat마다 연장된다. */
        val LOCK_TTL: Duration = Duration.ofMinutes(3)
    }
}
