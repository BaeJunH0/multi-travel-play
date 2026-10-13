package demo.travel.budget.presentation

import demo.travel.auth.resolver.CurrentUser
import demo.travel.budget.application.BudgetService
import demo.travel.budget.application.dto.BudgetCommand
import demo.travel.budget.presentation.dto.BudgetRequest
import demo.travel.budget.presentation.dto.BudgetResponse
import demo.travel.user.User
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/trips/{tripId}/budget")
class BudgetController(private val budgetService: BudgetService) {

    @GetMapping
    fun getItems(@CurrentUser user: User, @PathVariable tripId: UUID): List<BudgetResponse> =
        budgetService.getItems(tripId, user.id).map { BudgetResponse.of(it) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @Valid @RequestBody request: BudgetRequest.Create,
    ): BudgetResponse = BudgetResponse.of(
        budgetService.create(
            BudgetCommand.Create(
                tripId = tripId, category = request.category, amount = request.amount, memo = request.memo, userId = user.id,
            )
        )
    )

    @PatchMapping("/{itemId}")
    fun update(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @PathVariable itemId: UUID,
        @RequestBody request: BudgetRequest.Update,
    ): BudgetResponse = BudgetResponse.of(
        budgetService.update(
            BudgetCommand.Update(
                tripId = tripId, itemId = itemId, category = request.category, amount = request.amount, memo = request.memo, userId = user.id,
            )
        )
    )

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @PathVariable itemId: UUID,
    ) = budgetService.delete(BudgetCommand.Delete(tripId = tripId, itemId = itemId, userId = user.id))
}
