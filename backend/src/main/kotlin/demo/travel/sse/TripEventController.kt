package demo.travel.sse

import demo.travel.auth.resolver.CurrentUser
import demo.travel.user.User
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.*

@RestController
@RequestMapping("/api/trips/{tripId}")
class TripEventController(
    private val tripEventStream: TripEventStream,
) {
    @GetMapping("/events")
    fun events(@CurrentUser user: User, @PathVariable tripId: UUID): SseEmitter =
        tripEventStream.connect(tripId, user)

    @PostMapping("/presence")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun presence(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @RequestBody request: PresenceRequest,
    ) = tripEventStream.updatePresence(tripId, user.id, request.activeDay)
}

data class PresenceRequest(
    val activeDay: Int?,
)
