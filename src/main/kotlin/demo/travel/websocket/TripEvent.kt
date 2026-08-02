package demo.travel.websocket

import demo.travel.block.presentation.dto.BlockResponse
import java.util.*

data class TripEvent(
    val action: Action,
    val tripId: UUID,
    val payload: Any,
) {
    enum class Action { ADD, UPDATE, MOVE, DELETE, LOCK, UNLOCK, CONFLICT, PRESENCE }

    companion object {
        fun add(tripId: UUID, block: BlockResponse) =
            TripEvent(Action.ADD, tripId, block)

        fun update(tripId: UUID, block: BlockResponse) =
            TripEvent(Action.UPDATE, tripId, UpdatePayload(block.id, block))

        fun move(tripId: UUID, block: BlockResponse) =
            TripEvent(Action.MOVE, tripId, MovePayload(block.id, block.dayNumber, block.position, block.version))

        fun delete(tripId: UUID, blockId: UUID) =
            TripEvent(Action.DELETE, tripId, mapOf("blockId" to blockId))

        fun lock(tripId: UUID, block: BlockResponse) =
            TripEvent(Action.LOCK, tripId, LockPayload(block.id, block.lockedBy, block.lockedByNickname))

        fun unlock(tripId: UUID, blockId: UUID) =
            TripEvent(Action.UNLOCK, tripId, mapOf("blockId" to blockId))

        fun conflict(tripId: UUID, block: BlockResponse) =
            TripEvent(Action.CONFLICT, tripId, ConflictPayload(block.id, block))
    }
}

data class UpdatePayload(val blockId: UUID, val block: BlockResponse)
data class MovePayload(val blockId: UUID, val dayNumber: Int, val position: Double, val version: Long)
data class LockPayload(val blockId: UUID, val lockedBy: UUID?, val lockedByNickname: String?)
data class ConflictPayload(val blockId: UUID, val currentBlock: BlockResponse)
