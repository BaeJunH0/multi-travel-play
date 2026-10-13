package demo.travel.trip

import demo.travel.user.User
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "trip_members",
    uniqueConstraints = [UniqueConstraint(columnNames = ["trip_id", "user_id"])]
)
class TripMember(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    val trip: Trip,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var role: TripRole,

    @Column(nullable = false, updatable = false)
    val joinedAt: LocalDateTime = LocalDateTime.now(),
)

enum class TripRole { OWNER, EDITOR, VIEWER }
