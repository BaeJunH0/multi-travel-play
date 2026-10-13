package demo.travel.trip.application.dto

import demo.travel.trip.Trip
import demo.travel.trip.TripMember
import demo.travel.trip.TripRole
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

object TripResult {
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
            fun of(member: TripMember, memberCount: Int) = Summary(
                id = member.trip.id,
                title = member.trip.title,
                destination = member.trip.destination,
                startDate = member.trip.startDate,
                endDate = member.trip.endDate,
                memberCount = memberCount,
                myRole = member.role,
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
            fun of(trip: Trip, myRole: TripRole, memberCount: Int) = Detail(
                id = trip.id,
                title = trip.title,
                destination = trip.destination,
                startDate = trip.startDate,
                endDate = trip.endDate,
                memberCount = memberCount,
                myRole = myRole,
                days = (0..trip.startDate.until(trip.endDate, ChronoUnit.DAYS).toInt())
                    .map { i -> Day(i + 1, trip.startDate.plusDays(i.toLong())) },
            )
        }
    }

    data class Day(val dayNumber: Int, val date: LocalDate)
}
