package demo.travel.ai.presentation

import demo.travel.ai.application.AiService
import demo.travel.ai.presentation.dto.AiRequest
import demo.travel.auth.CurrentUser
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
        @Valid @RequestBody request: AiRequest.GenerateRequest,
    ) = aiService.generate(tripId, request, user.id)

    @PostMapping("/apply")
    fun apply(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @RequestBody request: AiRequest.ApplyRequest,
    ) = aiService.apply(tripId, request, user)
}