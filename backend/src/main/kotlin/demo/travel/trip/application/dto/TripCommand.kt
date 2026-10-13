package demo.travel.trip.application.dto

import java.time.LocalDate
import java.util.UUID

object TripCommand {
    data class Create(
        val title: String,
        val destination: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val userId: UUID,
    )

    data class Update(
        val tripId: UUID,
        val title: String?,
        val destination: String?,
        val startDate: LocalDate?,
        val endDate: LocalDate?,
        val userId: UUID,
    )
}
