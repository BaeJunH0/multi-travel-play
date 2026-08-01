package demo.travel.ai

import demo.travel.ai.dto.AiRequest
import demo.travel.auth.CurrentUser
import demo.travel.user.User
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/trips/{tripId}/ai")
class AiController(private val aiService: AiService) {

    @PostMapping("/generate")
    fun generate(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @Valid @RequestBody request: AiRequest.GenerateRequest,
    ) = aiService.generate(tripId, request, user.id)

    @PostMapping("/apply")
    fun apply(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @RequestBody request: AiRequest.ApplyRequest,
    ) = aiService.apply(tripId, request, user)
}
