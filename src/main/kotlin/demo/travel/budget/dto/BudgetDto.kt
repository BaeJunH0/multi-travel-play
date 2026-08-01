package demo.travel.budget.dto

import demo.travel.budget.BudgetCategory
import demo.travel.budget.BudgetItem
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class BudgetItemResponse(
    val id: UUID,
    val category: BudgetCategory,
    val amount: Int,
    val memo: String?,
) {
    companion object {
        fun of(item: BudgetItem) = BudgetItemResponse(
            id = item.id,
            category = item.category,
            amount = item.amount,
            memo = item.memo,
        )
    }
}

data class CreateBudgetRequest(
    @field:NotNull val category: BudgetCategory,
    @field:Min(0) val amount: Int,
    val memo: String?,
)

data class UpdateBudgetRequest(
    val category: BudgetCategory?,
    @field:Min(0) val amount: Int?,
    val memo: String?,
)
