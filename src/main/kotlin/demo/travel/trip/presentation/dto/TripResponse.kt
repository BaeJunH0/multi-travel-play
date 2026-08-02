package demo.travel.trip.presentation.dto

import demo.travel.trip.TripRole
import demo.travel.trip.application.dto.TripResult
import java.time.LocalDate
import java.util.*

object TripResponse {
    data class Summary(
        val id: UUID,
        val title: String,
        val destination: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val memberCount: Int,
        val myRole: TripRole,
    ) {
        companion object {
            fun of(result: TripResult.Summary) = Summary(
                id = result.id,
                title = result.title,
                destination = result.destination,
                startDate = result.startDate,
                endDate = result.endDate,
                memberCount = result.memberCount,
                myRole = result.myRole,
            )
        }
    }

    data class Detail(
        val id: UUID,
        val title: String,
        val destination: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val memberCount: Int,
        val myRole: TripRole,
        val days: List<Day>,
    ) {
        companion object {
            fun of(result: TripResult.Detail) = Detail(
                id = result.id,
                title = result.title,
                destination = result.destination,
                startDate = result.startDate,
                endDate = result.endDate,
                memberCount = result.memberCount,
                myRole = result.myRole,
                days = result.days.map { Day(it.dayNumber, it.date) },
            )
        }
    }

    data class Day(val dayNumber: Int, val date: LocalDate)
}
