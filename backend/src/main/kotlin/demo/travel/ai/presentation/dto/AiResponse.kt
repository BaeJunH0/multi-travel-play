package demo.travel.ai.presentation.dto

import demo.travel.block.presentation.dto.BlockResponse
import demo.travel.common.TripCategory

/** `generate` API 응답. DB에는 아무것도 저장되지 않은, AI가 제안한 블록 목록. */
data class GenerateResponse(
    val blocks: List<AiBlock>,
)

/**
 * AI가 제안한 일정 블록 1건. 아직 DB에 저장되지 않은 "제안" 상태이며,
 * 사용자가 `apply`로 선택하기 전까지는 [tempId] 기준으로만 식별된다.
 *
 * @param tempId AI 응답 내에서만 유효한 임시 식별자 (형식: `temp-{n}`). DB PK가 아님.
 * @param suggestedDay AI가 제안한 Day. 사용자가 `apply` 시 [AiRequest.SelectedBlock.dayNumber]로
 *   다른 Day를 지정하면 무시된다.
 */
data class AiBlock(
    val tempId: String,
    val blockType: TripCategory,
    val placeName: String,
    val startTime: String?,
    val durationMin: Int?,
    val cost: Int?,
    val suggestedDay: Int,
    val memo: String?,
)

/** `apply` API 응답. 실제로 DB에 저장된 블록 목록. */
data class ApplyResponse(val addedBlocks: List<AppliedBlock>)

/**
 * @param tempId 저장 요청에 쓰였던 원본 [AiBlock.tempId]. 프론트에서 어떤 AI 제안이
 *   어떤 저장 결과([block])로 이어졌는지 매칭할 때 사용.
 */
data class AppliedBlock(
    val tempId: String,
    val block: BlockResponse,
)
