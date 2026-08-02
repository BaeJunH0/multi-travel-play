package demo.travel.budget

import demo.travel.common.TripCategory
import demo.travel.trip.Trip
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "budget_items")
class BudgetItem(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    val trip: Trip,

    @Column(name = "block_id")
    var blockId: UUID? = null,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var category: TripCategory,

    @Column(nullable = false)
    var amount: Int,

    @Column(length = 255)
    var memo: String? = null,
)
