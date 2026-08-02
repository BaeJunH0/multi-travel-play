package demo.travel.budget.presentation.dto

import demo.travel.budget.application.dto.BudgetResult
import demo.travel.common.TripCategory
import java.util.UUID

data class BudgetResponse(
    val id: UUID,
    val blockId: UUID?,
    val category: TripCategory,
    val amount: Int,
    val memo: String?,
) {
    companion object {
        fun of(result: BudgetResult) = BudgetResponse(
            id = result.id,
            blockId = result.blockId,
            category = result.category,
            amount = result.amount,
            memo = result.memo,
        )
    }
}
