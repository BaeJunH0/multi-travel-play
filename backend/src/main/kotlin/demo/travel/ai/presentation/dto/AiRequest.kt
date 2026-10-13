package demo.travel.ai.presentation.dto

import demo.travel.common.TripCategory

/** AI 일정 생성(`generate`) / 확정 저장(`apply`) API의 요청 DTO 모음. */
object AiRequest {
    /**
     * AI에게 일정 블록 생성을 요청할 때 사용.
     *
     * @param tags 생성할 블록 타입 필터. 비어있으면 서버에서 "전체"로 취급.
     * @param targetDays 생성 결과를 적용할 Day 목록. 비어있으면 서버에서 "전체"로 취급.
     */
    data class GenerateRequest(
        val tags: List<TripCategory>,
        val targetDays: List<Int>,
        val style: String,
        val intensity: String,
        val extraRequest: String?,
    )

    /**
     * `generate` 응답으로 받은 블록 중 사용자가 선택한 항목을 실제 DB에 저장 요청.
     * 서버는 별도로 원본 상태를 들고 있지 않으므로(stateless), 저장할 블록의 내용을
     * [SelectedBlock]에 그대로 담아 보낸다.
     */
    data class ApplyRequest(
        val selectedBlocks: List<SelectedBlock>,
    )

    /**
     * 사용자가 채택한 AI 블록 1건. [AiBlock]의 내용과 사용자가 정한 배치 정보를
     * 함께 담아 서버에 그대로 저장 요청한다.
     *
     * @param tempId 원본 [AiBlock.tempId]. 서버는 저장 결과([AppliedBlock])를 응답할 때
     *   그대로 echo하여, 프론트가 어떤 AI 제안이 어떤 저장 결과로 이어졌는지 매칭하게 한다.
     * @param dayNumber 사용자가 최종적으로 배치한 Day (AI의 [AiBlock.suggestedDay]와 다를 수 있음).
     * @param position 같은 Day 내에서의 정렬 순서.
     */
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
