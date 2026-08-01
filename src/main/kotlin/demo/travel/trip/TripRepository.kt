package demo.travel.trip

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.*

interface TripRepository : JpaRepository<Trip, UUID> {
    fun findByShareToken(shareToken: String): Trip?
}

interface TripMemberRepository : JpaRepository<TripMember, UUID> {
    fun findByTripIdAndUserId(tripId: UUID, userId: UUID): TripMember?
    fun findByTripIdAndRole(tripId: UUID, role: TripRole): TripMember?
    fun countByTripId(tripId: UUID): Int
    fun findAllByTripId(tripId: UUID): List<TripMember>

    @Query("SELECT tm FROM TripMember tm JOIN FETCH tm.trip WHERE tm.user.id = :userId")
    fun findAllWithTripByUserId(userId: UUID): List<TripMember>
}
