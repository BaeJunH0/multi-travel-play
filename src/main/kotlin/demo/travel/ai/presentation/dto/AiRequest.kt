package demo.travel.ai.presentation.dto

import demo.travel.block.BlockType

/** AI 일정 생성(`generate`) / 확정 저장(`apply`) API의 요청 DTO 모음. */
object AiRequest {
    /**
     * AI에게 일정 블록 생성을 요청할 때 사용.
     *
     * @param tags 생성할 블록 타입 필터. 비어있으면 서버에서 "전체"로 취급.
     * @param targetDays 생성 결과를 적용할 Day 목록. 비어있으면 서버에서 "전체"로 취급.
     */
    data class GenerateRequest(
        val tags: List<BlockType>,
        val targetDays: List<Int>,
        val style: String,
        val intensity: String,
        val extraRequest: String?,
    )

    /**
     * `generate` 응답으로 받은 블록 중 사용자가 선택한 항목을 실제 DB에 저장 요청.
     *
     * @param generationId `generate` 응답의 [GenerateResponse.generationId]. 서버가 이 값으로
     *   Redis에 임시 저장된 AI 생성 결과(TTL 10분)를 찾아 [SelectedBlock]과 병합한다.
     */
    data class ApplyRequest(
        val generationId: String,
        val selectedBlocks: List<SelectedBlock>,
    )

    /**
     * 사용자가 채택한 AI 블록 1건. 블록의 실제 내용(placeName, cost 등)은 담지 않고,
     * 원본 [AiBlock]을 가리키는 참조([tempId])와 사용자가 정한 배치 정보만 전달한다.
     *
     * @param tempId 원본 [AiBlock.tempId]. 서버는 이 값으로 Redis에 저장된 AI 블록을 찾아온다.
     * @param dayNumber 사용자가 최종적으로 배치한 Day (AI의 [AiBlock.suggestedDay]와 다를 수 있음).
     * @param position 같은 Day 내에서의 정렬 순서.
     */
    data class SelectedBlock(
        val tempId: String,
        val dayNumber: Int,
        val position: Double,
    )
}
