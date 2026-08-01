package demo.travel.trip

import demo.travel.auth.resolver.CurrentUser
import demo.travel.user.User
import demo.travel.trip.dto.TripRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/trips")
class TripController(private val tripService: TripService) {

    @GetMapping
    fun getList(@CurrentUser user: User) =
        tripService.getList(user.id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@CurrentUser user: User, @Valid @RequestBody request: TripRequest.Create) =
        tripService.create(request, user)

    @GetMapping("/{tripId}")
    fun getDetail(@CurrentUser user: User, @PathVariable tripId: UUID) =
        tripService.getDetail(tripId, user.id)

    @PatchMapping("/{tripId}")
    fun update(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @RequestBody request: TripRequest.Update,
    ) = tripService.update(tripId, request, user.id)

    @DeleteMapping("/{tripId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@CurrentUser user: User, @PathVariable tripId: UUID) =
        tripService.delete(tripId, user.id)
}
