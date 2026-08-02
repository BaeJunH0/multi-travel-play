package demo.travel.budget.application.dto

import demo.travel.budget.BudgetItem
import demo.travel.common.TripCategory
import java.util.UUID

data class BudgetResult(
    val id: UUID,
    val blockId: UUID?,
    val category: TripCategory,
    val amount: Int,
    val memo: String?,
) {
    companion object {
        fun of(item: BudgetItem) = BudgetResult(
            id = item.id,
            blockId = item.blockId,
            category = item.category,
            amount = item.amount,
            memo = item.memo,
        )
    }
}
