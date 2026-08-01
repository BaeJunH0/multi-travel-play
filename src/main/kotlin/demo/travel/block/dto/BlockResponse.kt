package demo.travel.block.dto

import demo.travel.block.BlockType
import demo.travel.block.ScheduleBlock
import java.util.*

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
        fun of(block: ScheduleBlock) = BlockResponse(
            id = block.id,
            dayNumber = block.dayNumber,
            position = block.position,
            blockType = block.blockType,
            placeName = block.placeName,
            lat = block.lat,
            lng = block.lng,
            startTime = block.startTime?.toString(),
            durationMin = block.durationMin,
            cost = block.cost,
            memo = block.memo,
            lockedBy = block.lockedBy?.id,
            lockedByNickname = block.lockedBy?.nickname,
            version = block.version,
        )
    }
}
