package demo.travel.block.event

import demo.travel.block.ScheduleBlock
import demo.travel.common.TripCategory
import java.util.UUID

data class BlockCostChangedEvent(
    val blockId: UUID,
    val tripId: UUID,
    val category: TripCategory,
    val cost: Int,
    val placeName: String,
)

data class BlockDeletedEvent(val blockId: UUID)

fun ScheduleBlock.toCostChangedEventOrNull(): BlockCostChangedEvent? {
    val cost = this.cost ?: return null
    return BlockCostChangedEvent(
        blockId = id,
        tripId = trip.id,
        category = blockType,
        cost = cost,
        placeName = placeName,
    )
}
