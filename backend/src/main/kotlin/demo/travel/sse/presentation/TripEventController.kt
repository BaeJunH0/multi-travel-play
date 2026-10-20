package demo.travel.sse.presentation

import demo.travel.auth.resolver.CurrentUser
import demo.travel.sse.application.TripEventService
import demo.travel.sse.presentation.dto.PresenceRequest
import demo.travel.user.User
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.*

@RestController
@RequestMapping("/api/trips/{tripId}")
class TripEventController(private val tripEventService: TripEventService) {
    @GetMapping("/events")
    fun events(@CurrentUser user: User, @PathVariable tripId: UUID): SseEmitter = tripEventService.connect(tripId, user)

    @PostMapping("/presence")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun presence(@CurrentUser user: User, @PathVariable tripId: UUID, @RequestBody request: PresenceRequest) =
        tripEventService.updatePresence(tripId, user.id, request.activeDay)
}
