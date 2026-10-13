package demo.travel.trip.presentation

import demo.travel.auth.resolver.CurrentUser
import demo.travel.trip.application.TripService
import demo.travel.trip.application.dto.TripCommand
import demo.travel.trip.presentation.dto.TripRequest
import demo.travel.trip.presentation.dto.TripResponse
import demo.travel.user.User
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/trips")
class TripController(private val tripService: TripService) {

    @GetMapping
    fun getList(@CurrentUser user: User): List<TripResponse.Summary> =
        tripService.getList(user.id).map { TripResponse.Summary.of(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@CurrentUser user: User, @Valid @RequestBody request: TripRequest.Create): TripResponse.Detail =
        TripResponse.Detail.of(
            tripService.create(
                TripCommand.Create(
                    title = request.title,
                    destination = request.destination,
                    startDate = request.startDate,
                    endDate = request.endDate,
                    userId = user.id,
                )
            )
        )

    @GetMapping("/{tripId}")
    fun getDetail(@CurrentUser user: User, @PathVariable tripId: UUID): TripResponse.Detail =
        TripResponse.Detail.of(tripService.getDetail(tripId, user.id))

    @PatchMapping("/{tripId}")
    fun update(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @RequestBody request: TripRequest.Update,
    ): TripResponse.Detail = TripResponse.Detail.of(
        tripService.update(
            TripCommand.Update(
                tripId = tripId,
                title = request.title,
                destination = request.destination,
                startDate = request.startDate,
                endDate = request.endDate,
                userId = user.id,
            )
        )
    )

    @DeleteMapping("/{tripId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@CurrentUser user: User, @PathVariable tripId: UUID) =
        tripService.delete(tripId, user.id)
}
