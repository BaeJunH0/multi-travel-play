package demo.travel.block.presentation

import demo.travel.auth.resolver.CurrentUser
import demo.travel.block.application.BlockService
import demo.travel.block.application.dto.BlockCommand
import demo.travel.block.presentation.dto.BlockRequest
import demo.travel.block.presentation.dto.BlockResponse
import demo.travel.user.User
import demo.travel.websocket.TripEvent
import demo.travel.websocket.TripEventPublisher
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/trips/{tripId}/blocks")
class BlockController(
    private val blockService: BlockService,
    private val eventPublisher: TripEventPublisher,
) {
    @GetMapping
    fun getBlocks(@CurrentUser user: User, @PathVariable tripId: UUID): List<BlockResponse> =
        blockService.getBlocks(tripId, user.id).map { BlockResponse.of(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun addBlock(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @Valid @RequestBody request: BlockRequest.Create,
    ): BlockResponse = BlockResponse.of(
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
            )
        )
    ).also { eventPublisher.publish(tripId, TripEvent.add(tripId, it)) }

    @PatchMapping("/{blockId}")
    fun updateBlock(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @PathVariable blockId: UUID,
        @Valid @RequestBody request: BlockRequest.Update,
    ): BlockResponse = BlockResponse.of(
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
            )
        )
    ).also { eventPublisher.publish(tripId, TripEvent.update(tripId, it)) }

    @PatchMapping("/{blockId}/move")
    fun moveBlock(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @PathVariable blockId: UUID,
        @Valid @RequestBody request: BlockRequest.Move,
    ): BlockResponse = BlockResponse.of(
        blockService.moveBlock(
            BlockCommand.Move(
                tripId = tripId,
                blockId = blockId,
                dayNumber = request.dayNumber,
                position = request.position,
                version = request.version,
                userId = user.id,
            )
        )
    ).also { eventPublisher.publish(tripId, TripEvent.move(tripId, it)) }

    @DeleteMapping("/{blockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteBlock(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @PathVariable blockId: UUID,
    ) {
        blockService.deleteBlock(tripId, blockId, user.id)
        eventPublisher.publish(tripId, TripEvent.delete(tripId, blockId))
    }

    @PostMapping("/{blockId}/lock")
    @ResponseStatus(HttpStatus.OK)
    fun lockBlock(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @PathVariable blockId: UUID,
    ): BlockResponse = BlockResponse.of(blockService.lockBlock(tripId, blockId, user.id))
        .also { eventPublisher.publish(tripId, TripEvent.lock(tripId, it)) }

    @DeleteMapping("/{blockId}/lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unlockBlock(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @PathVariable blockId: UUID,
    ) {
        blockService.unlockBlock(tripId, blockId, user.id)
        eventPublisher.publish(tripId, TripEvent.unlock(tripId, blockId))
    }

    @PostMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reorder(@CurrentUser user: User, @PathVariable tripId: UUID) =
        blockService.reorderBlocks(tripId, user.id)
}
