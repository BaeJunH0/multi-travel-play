package demo.travel.budget.application.dto

import demo.travel.common.TripCategory
import java.util.UUID

object BudgetCommand {
    data class Create(
        val tripId: UUID,
        val category: TripCategory,
        val amount: Int,
        val memo: String?,
        val userId: UUID,
    )

    data class Update(
        val tripId: UUID,
        val itemId: UUID,
        val category: TripCategory?,
        val amount: Int?,
        val memo: String?,
        val userId: UUID,
    )

    data class Delete(
        val tripId: UUID,
        val itemId: UUID,
        val userId: UUID,
    )
}
