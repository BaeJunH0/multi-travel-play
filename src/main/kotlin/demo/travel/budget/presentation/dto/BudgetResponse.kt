package demo.travel.budget.presentation.dto

import demo.travel.budget.BudgetCategory
import demo.travel.budget.application.dto.BudgetResult
import java.util.UUID

data class BudgetResponse(
    val id: UUID,
    val category: BudgetCategory,
    val amount: Int,
    val memo: String?,
) {
    companion object {
        fun of(result: BudgetResult) = BudgetResponse(
            id = result.id,
            category = result.category,
            amount = result.amount,
            memo = result.memo,
        )
    }
}
