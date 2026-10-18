package demo.travel.ai.application.dto

import demo.travel.ai.client.dto.AiBlock
import demo.travel.block.application.dto.BlockResult
import demo.travel.common.TripCategory

object AiResult {
    /** `generate` 결과. DB에는 아무것도 저장되지 않은, AI가 제안한 블록 목록. */
    data class Generate(val blocks: List<SuggestedBlock>)

    data class SuggestedBlock(
        val tempId: String,
        val blockType: TripCategory,
        val placeName: String,
        val startTime: String?,
        val durationMin: Int?,
        val cost: Int?,
        val suggestedDay: Int,
        val memo: String?,
    ) {
        companion object {
            fun of(block: AiBlock) = SuggestedBlock(
                tempId = block.tempId,
                blockType = block.blockType,
                placeName = block.placeName,
                startTime = block.startTime,
                durationMin = block.durationMin,
                cost = block.cost,
                suggestedDay = block.suggestedDay,
                memo = block.memo,
            )
        }
    }

    /** `apply` 결과. 실제로 DB에 저장된 블록 목록. */
    data class Apply(val addedBlocks: List<AppliedBlock>)

    /** @param tempId 저장 요청에 쓰였던 원본 tempId. 프론트가 제안과 저장 결과를 매칭할 때 쓴다. */
    data class AppliedBlock(
        val tempId: String,
        val block: BlockResult,
    )
}
