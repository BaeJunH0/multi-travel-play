package demo.travel.trip.dto

import demo.travel.trip.Trip
import demo.travel.trip.TripMember
import demo.travel.trip.TripRole
import java.time.LocalDate
import java.util.*

data class TripSummaryResponse(
    val id: UUID,
    val title: String,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val memberCount: Int,
    val myRole: TripRole,
) {
    companion object {
        fun of(member: TripMember, memberCount: Int) = TripSummaryResponse(
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

data class TripDetailResponse(
    val id: UUID,
    val title: String,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val memberCount: Int,
    val myRole: TripRole,
    val days: List<DayResponse>,
) {
    companion object {
        fun of(trip: Trip, myRole: TripRole, memberCount: Int) = TripDetailResponse(
            id = trip.id,
            title = trip.title,
            destination = trip.destination,
            startDate = trip.startDate,
            endDate = trip.endDate,
            memberCount = memberCount,
            myRole = myRole,
            days = (0..trip.startDate.until(trip.endDate, java.time.temporal.ChronoUnit.DAYS).toInt())
                .map { i -> DayResponse(i + 1, trip.startDate.plusDays(i.toLong())) },
        )
    }
}

data class DayResponse(val dayNumber: Int, val date: LocalDate)
