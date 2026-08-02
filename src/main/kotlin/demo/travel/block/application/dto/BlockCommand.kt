package demo.travel.block.application.dto

import demo.travel.common.TripCategory
import java.util.UUID

object BlockCommand {
    data class Create(
        val tripId: UUID,
        val dayNumber: Int,
        val blockType: TripCategory,
        val placeName: String,
        val startTime: String?,
        val durationMin: Int?,
        val cost: Int?,
        val memo: String?,
        val userId: UUID,
    )

    data class Update(
        val tripId: UUID,
        val blockId: UUID,
        val placeName: String?,
        val startTime: String?,
        val durationMin: Int?,
        val cost: Int?,
        val memo: String?,
        val version: Long,
        val userId: UUID,
    )

    data class Move(
        val tripId: UUID,
        val blockId: UUID,
        val dayNumber: Int,
        val position: Double,
        val version: Long,
        val userId: UUID,
    )
}
