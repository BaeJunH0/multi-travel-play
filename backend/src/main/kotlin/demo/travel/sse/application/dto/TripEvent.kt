package demo.travel.sse.application.dto

import demo.travel.block.application.dto.BlockResult
import java.util.*

data class TripEvent(
    val action: Action,
    val tripId: UUID,
    val payload: Any,
) {
    enum class Action { ADD, UPDATE, MOVE, DELETE, LOCK, UNLOCK, CONFLICT, PRESENCE }

    companion object {
        fun add(tripId: UUID, block: BlockResult) =
            TripEvent(Action.ADD, tripId, block)

        fun update(tripId: UUID, block: BlockResult) =
            TripEvent(Action.UPDATE, tripId, UpdatePayload(block.id, block))

        fun move(tripId: UUID, block: BlockResult) =
            TripEvent(Action.MOVE, tripId, MovePayload(block.id, block.dayNumber, block.position, block.version))

        fun delete(tripId: UUID, blockId: UUID) =
            TripEvent(Action.DELETE, tripId, BlockIdPayload(blockId))

        fun lock(tripId: UUID, block: BlockResult) =
            TripEvent(Action.LOCK, tripId, LockPayload(block.id, block.lockedBy, block.lockedByNickname))

        fun unlock(tripId: UUID, blockId: UUID) =
            TripEvent(Action.UNLOCK, tripId, BlockIdPayload(blockId))

        fun conflict(tripId: UUID, block: BlockResult) =
            TripEvent(Action.CONFLICT, tripId, ConflictPayload(block.id, block))

        fun presence(tripId: UUID, users: List<PresenceUser>) =
            TripEvent(Action.PRESENCE, tripId, PresencePayload(users))
    }
}

data class UpdatePayload(val blockId: UUID, val block: BlockResult)
data class MovePayload(val blockId: UUID, val dayNumber: Int, val position: Double, val version: Long)
data class BlockIdPayload(val blockId: UUID)
data class LockPayload(val blockId: UUID, val lockedBy: UUID?, val lockedByNickname: String?)
data class ConflictPayload(val blockId: UUID, val currentBlock: BlockResult)
data class PresencePayload(val users: List<PresenceUser>)
