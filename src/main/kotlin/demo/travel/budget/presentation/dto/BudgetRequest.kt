package demo.travel.budget.presentation.dto

import demo.travel.budget.BudgetCategory
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

object BudgetRequest {
    data class Create(
        @field:NotNull val category: BudgetCategory,
        @field:Min(0) val amount: Int,
        val memo: String?,
    )

    data class Update(
        val category: BudgetCategory?,
        @field:Min(0) val amount: Int?,
        val memo: String?,
    )
}
