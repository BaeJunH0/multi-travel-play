package demo.travel.block

import demo.travel.common.TripCategory
import demo.travel.trip.Trip
import demo.travel.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.OptimisticLock
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

@Entity
@DynamicUpdate
@Table(name = "schedule_blocks")
class ScheduleBlock(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    val trip: Trip,

    @Column(nullable = false)
    var dayNumber: Int,

    @Column(nullable = false)
    var position: Double,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var blockType: TripCategory,

    @Column(nullable = false, length = 255)
    var placeName: String,

    var lat: Double? = null,
    var lng: Double? = null,
    var startTime: LocalTime? = null,
    var durationMin: Int? = null,
    var cost: Int? = null,

    @Column(columnDefinition = "TEXT")
    var memo: String? = null,

    @Version
    var version: Long = 0,

    @OptimisticLock(excluded = true)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locked_by")
    var lockedBy: User? = null,

    // 잠금 만료 시각. 잠금자의 SSE 연결이 살아 있는 동안 heartbeat마다 연장된다
    @OptimisticLock(excluded = true)
    var lockExpiresAt: LocalDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    val createdBy: User,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @OptimisticLock(excluded = true)
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
