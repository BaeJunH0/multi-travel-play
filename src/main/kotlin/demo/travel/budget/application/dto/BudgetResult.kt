package demo.travel.budget.application.dto

import demo.travel.budget.BudgetCategory
import demo.travel.budget.BudgetItem
import java.util.UUID

data class BudgetResult(
    val id: UUID,
    val category: BudgetCategory,
    val amount: Int,
    val memo: String?,
) {
    companion object {
        fun of(item: BudgetItem) = BudgetResult(
            id = item.id,
            category = item.category,
            amount = item.amount,
            memo = item.memo,
        )
    }
}
