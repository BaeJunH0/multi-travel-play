package demo.travel.budget

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

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var category: BudgetCategory,

    @Column(nullable = false)
    var amount: Int,

    @Column(length = 255)
    var memo: String? = null,
)

enum class BudgetCategory { FLIGHT, HOTEL, FOOD, TRANSPORT, ETC }
