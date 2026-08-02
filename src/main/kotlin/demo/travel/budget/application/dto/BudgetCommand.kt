package demo.travel.budget.application.dto

import demo.travel.budget.BudgetCategory
import java.util.UUID

object BudgetCommand {
    data class Create(
        val tripId: UUID,
        val category: BudgetCategory,
        val amount: Int,
        val memo: String?,
        val userId: UUID,
    )

    data class Update(
        val tripId: UUID,
        val itemId: UUID,
        val category: BudgetCategory?,
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
