package demo.travel.ai.presentation.dto

import demo.travel.block.dto.BlockResponse
import demo.travel.block.BlockType

data class GenerateResponse(
    val generationId: String,
    val blocks: List<AiBlock>,
)

data class AiBlock(
    val tempId: String,
    val blockType: BlockType,
    val placeName: String,
    val startTime: String?,
    val durationMin: Int?,
    val cost: Int?,
    val suggestedDay: Int,
    val memo: String?,
)

data class ApplyResponse(val addedBlocks: List<AppliedBlock>)

data class AppliedBlock(
    val tempId: String,
    val block: BlockResponse,
)
