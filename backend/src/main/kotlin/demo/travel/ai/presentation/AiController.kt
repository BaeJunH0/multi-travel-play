package demo.travel.ai.presentation

import demo.travel.ai.application.AiService
import demo.travel.ai.application.dto.AiCommand
import demo.travel.ai.presentation.dto.AiRequest
import demo.travel.ai.presentation.dto.AiResponse
import demo.travel.auth.resolver.CurrentUser
import demo.travel.user.User
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/trips/{tripId}/ai")
class AiController(private val aiService: AiService) {

    @PostMapping("/generate")
    fun generate(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @Valid @RequestBody request: AiRequest.Generate,
    ): AiResponse.Generate = AiResponse.Generate.of(
        aiService.generate(
            AiCommand.Generate(
                tripId = tripId,
                tags = request.tags,
                targetDays = request.targetDays,
                style = request.style,
                intensity = request.intensity,
                extraRequest = request.extraRequest,
                userId = user.id,
            ),
        ),
    )

    @PostMapping("/apply")
    fun apply(@CurrentUser user: User, @PathVariable tripId: UUID, @RequestBody request: AiRequest.Apply): AiResponse.Apply =
        AiResponse.Apply.of(
            aiService.apply(
                AiCommand.Apply(
                    tripId = tripId,
                    selectedBlocks = request.selectedBlocks.map {
                        AiCommand.SelectedBlock(
                            tempId = it.tempId,
                            blockType = it.blockType,
                            placeName = it.placeName,
                            startTime = it.startTime,
                            durationMin = it.durationMin,
                            cost = it.cost,
                            memo = it.memo,
                            dayNumber = it.dayNumber,
                            position = it.position,
                        )
                    },
                    userId = user.id,
                ),
            ),
        )
}
