package demo.travel.ai.application.dto

import demo.travel.common.TripCategory
import java.util.UUID

object AiCommand {
    /**
     * @param tags 생성할 블록 타입 필터. 비어있으면 "전체"로 취급.
     * @param targetDays 생성 결과를 적용할 Day 목록. 비어있으면 "전체"로 취급.
     */
    data class Generate(
        val tripId: UUID,
        val tags: List<TripCategory>,
        val targetDays: List<Int>,
        val style: String,
        val intensity: String,
        val extraRequest: String?,
        val userId: UUID,
    )

    data class Apply(val tripId: UUID, val selectedBlocks: List<SelectedBlock>, val userId: UUID)

    /** 사용자가 채택한 AI 블록 1건과 사용자가 정한 배치(dayNumber, position). */
    data class SelectedBlock(
        val tempId: String,
        val blockType: TripCategory,
        val placeName: String,
        val startTime: String?,
        val durationMin: Int?,
        val cost: Int?,
        val memo: String?,
        val dayNumber: Int,
        val position: Double,
    )
}
