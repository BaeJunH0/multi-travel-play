package demo.travel.budget

import demo.travel.auth.resolver.CurrentUser
import demo.travel.budget.dto.CreateBudgetRequest
import demo.travel.budget.dto.UpdateBudgetRequest
import demo.travel.user.User
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/trips/{tripId}/budget")
class BudgetController(private val budgetService: BudgetService) {

    @GetMapping
    fun getItems(@CurrentUser user: User, @PathVariable tripId: UUID) =
        budgetService.getItems(tripId, user.id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @Valid @RequestBody request: CreateBudgetRequest,
    ) = budgetService.create(tripId, request, user.id)

    @PatchMapping("/{itemId}")
    fun update(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @PathVariable itemId: UUID,
        @RequestBody request: UpdateBudgetRequest,
    ) = budgetService.update(tripId, itemId, request, user.id)

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @CurrentUser user: User,
        @PathVariable tripId: UUID,
        @PathVariable itemId: UUID,
    ) = budgetService.delete(tripId, itemId, user.id)
}
