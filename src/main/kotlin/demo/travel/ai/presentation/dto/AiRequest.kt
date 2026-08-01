package demo.travel.ai.presentation.dto

import demo.travel.block.BlockType

object AiRequest {
    data class GenerateRequest(
        val tags: List<BlockType>,
        val targetDays: List<Int>,
        val style: String,
        val intensity: String,
        val extraRequest: String?,
    )

    data class ApplyRequest(
        val generationId: String,
        val selectedBlocks: List<SelectedBlock>,
    )

    data class SelectedBlock(
        val tempId: String,
        val dayNumber: Int,
        val position: Double,
    )
}
