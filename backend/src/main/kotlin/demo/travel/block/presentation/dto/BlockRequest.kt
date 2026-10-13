package demo.travel.block.presentation.dto

import demo.travel.common.TripCategory
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

object BlockRequest {
    data class Create(
        @field:NotNull val dayNumber: Int,
        @field:NotNull val blockType: TripCategory,
        @field:NotBlank val placeName: String,
        val startTime: String?,
        val durationMin: Int?,
        val cost: Int?,
        val memo: String?,
    )

    data class Update(
        val placeName: String?,
        val startTime: String?,
        val durationMin: Int?,
        val cost: Int?,
        val memo: String?,
        @field:NotNull val version: Long,
    )

    data class Move(
        @field:NotNull val dayNumber: Int,
        @field:NotNull val position: Double,
        @field:NotNull val version: Long,
    )
}
