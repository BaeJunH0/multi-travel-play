package demo.travel.block.presentation.dto

import demo.travel.block.BlockType
import demo.travel.block.application.dto.BlockResult
import java.util.UUID

data class BlockResponse(
    val id: UUID,
    val dayNumber: Int,
    val position: Double,
    val blockType: BlockType,
    val placeName: String,
    val lat: Double?,
    val lng: Double?,
    val startTime: String?,
    val durationMin: Int?,
    val cost: Int?,
    val memo: String?,
    val lockedBy: UUID?,
    val lockedByNickname: String?,
    val version: Long,
) {
    companion object {
        fun of(result: BlockResult) = BlockResponse(
            id = result.id,
            dayNumber = result.dayNumber,
            position = result.position,
            blockType = result.blockType,
            placeName = result.placeName,
            lat = result.lat,
            lng = result.lng,
            startTime = result.startTime,
            durationMin = result.durationMin,
            cost = result.cost,
            memo = result.memo,
            lockedBy = result.lockedBy,
            lockedByNickname = result.lockedByNickname,
            version = result.version,
        )
    }
}
