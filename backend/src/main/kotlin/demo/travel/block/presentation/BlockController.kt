package demo.travel.block.presentation

import demo.travel.auth.resolver.CurrentUser
import demo.travel.block.application.BlockService
import demo.travel.block.application.VersionConflictException
import demo.travel.block.application.dto.BlockCommand
import demo.travel.block.presentation.dto.BlockRequest
import demo.travel.block.presentation.dto.BlockResponse
import demo.travel.sse.application.TripEventPublisher
import demo.travel.sse.application.dto.TripEvent
import demo.travel.user.User
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/api/trips/{tripId}/blocks")
class BlockController(private val blockService: BlockService, private val eventPublisher: TripEventPublisher) {
    @GetMapping
    fun getBlocks(@CurrentUser user: User, @PathVariable tripId: UUID): List<BlockResponse> =
        blockService.getBlocks(tripId, user.id).map { BlockResponse.of(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun addBlock(@CurrentUser user: User, @PathVariable tripId: UUID, @Valid @RequestBody request: BlockRequest.Create): BlockResponse =
        blockService.addBlock(
            BlockCommand.Create(
                tripId = tripId,
                dayNumber = request.dayNumber,
                blockType = request.blockType,
                placeName = request.placeName,
                startTime = request.startTime,
                durationMin = request.durationMin,
                cost = request.cost,
                memo = request.memo,
                userId = user.id,
            ),
        )
            .also { eventPublisher.publish(tripId, TripEvent.add(tripId, it)) }
            .let { BlockResponse.of(it) }

    @PatchMapping("/{blockId}")
    fun updateBlock(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @PathVariable blockId: UUID,
        @Valid @RequestBody request: BlockRequest.Update,
    ): BlockResponse = broadcastingConflict(tripId) {
        blockService.updateBlock(
            BlockCommand.Update(
                tripId = tripId,
                blockId = blockId,
                placeName = request.placeName,
                startTime = request.startTime,
                durationMin = request.durationMin,
                cost = request.cost,
                memo = request.memo,
                version = request.version,
                userId = user.id,
            ),
        )
    }
        .also { eventPublisher.publish(tripId, TripEvent.update(tripId, it)) }
        .let { BlockResponse.of(it) }

    @PatchMapping("/{blockId}/move")
    fun moveBlock(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @PathVariable blockId: UUID,
        @Valid @RequestBody request: BlockRequest.Move,
    ): BlockResponse = broadcastingConflict(tripId) {
        blockService.moveBlock(
            BlockCommand.Move(
                tripId = tripId,
                blockId = blockId,
                dayNumber = request.dayNumber,
                position = request.position,
                version = request.version,
                userId = user.id,
            ),
        )
    }
        .also { eventPublisher.publish(tripId, TripEvent.move(tripId, it)) }
        .let { BlockResponse.of(it) }

    @DeleteMapping("/{blockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteBlock(@CurrentUser user: User, @PathVariable tripId: UUID, @PathVariable blockId: UUID) {
        blockService.deleteBlock(tripId, blockId, user.id)
        eventPublisher.publish(tripId, TripEvent.delete(tripId, blockId))
    }

    @PostMapping("/{blockId}/lock")
    @ResponseStatus(HttpStatus.OK)
    fun lockBlock(@CurrentUser user: User, @PathVariable tripId: UUID, @PathVariable blockId: UUID): BlockResponse =
        blockService.lockBlock(tripId, blockId, user.id)
            .also { eventPublisher.publish(tripId, TripEvent.lock(tripId, it)) }
            .let { BlockResponse.of(it) }

    @DeleteMapping("/{blockId}/lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unlockBlock(@CurrentUser user: User, @PathVariable tripId: UUID, @PathVariable blockId: UUID) {
        blockService.unlockBlock(tripId, blockId, user.id)
        eventPublisher.publish(tripId, TripEvent.unlock(tripId, blockId))
    }

    /** position이 바뀐 블록을 돌려주고, 다른 접속자도 맞추도록 블록마다 MOVE를 브로드캐스트한다. */
    @PostMapping("/reorder")
    fun reorder(@CurrentUser user: User, @PathVariable tripId: UUID): List<BlockResponse> = blockService.reorderBlocks(tripId, user.id)
        .onEach { eventPublisher.publish(tripId, TripEvent.move(tripId, it)) }
        .map { BlockResponse.of(it) }

    /** version 충돌 시 다른 접속자에게도 CONFLICT를 알리고, 예외는 그대로 던져 요청자에게 409를 돌려준다. */
    private fun <T> broadcastingConflict(tripId: UUID, block: () -> T): T = try {
        block()
    } catch (e: VersionConflictException) {
        eventPublisher.publish(tripId, TripEvent.conflict(tripId, e.currentBlock))
        throw e
    }
}
