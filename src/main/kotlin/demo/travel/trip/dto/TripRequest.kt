package demo.travel.trip.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

object TripRequest {
    data class Create(
        @field:NotBlank val title: String,
        @field:NotBlank val destination: String,
        @field:NotNull val startDate: LocalDate,
        @field:NotNull val endDate: LocalDate,
    )

    data class Update(
        val title: String?,
        val destination: String?,
        val startDate: LocalDate?,
        val endDate: LocalDate?,
    )
}
