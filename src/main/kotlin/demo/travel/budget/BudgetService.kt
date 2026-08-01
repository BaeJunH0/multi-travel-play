package demo.travel.budget

import demo.travel.budget.dto.BudgetItemResponse
import demo.travel.budget.dto.CreateBudgetRequest
import demo.travel.budget.dto.UpdateBudgetRequest
import demo.travel.trip.TripMemberRepository
import demo.travel.trip.TripRepository
import demo.travel.trip.TripRole
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
@Transactional
class BudgetService(
    private val budgetRepository: BudgetRepository,
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
) {
    @Transactional(readOnly = true)
    fun getItems(tripId: UUID, userId: UUID): List<BudgetItemResponse> {
        requireMember(tripId, userId)
        return budgetRepository.findAllByTripId(tripId).map { BudgetItemResponse.of(it) }
    }

    fun create(tripId: UUID, request: CreateBudgetRequest, userId: UUID): BudgetItemResponse {
        requireEditorOrAbove(tripId, userId)
        val trip = tripRepository.findByIdOrNull(tripId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val item = budgetRepository.save(
            BudgetItem(trip = trip, category = request.category, amount = request.amount, memo = request.memo)
        )
        return BudgetItemResponse.of(item)
    }

    fun update(tripId: UUID, itemId: UUID, request: UpdateBudgetRequest, userId: UUID): BudgetItemResponse {
        requireEditorOrAbove(tripId, userId)
        val item = budgetRepository.findByIdOrNull(itemId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (item.trip.id != tripId) throw ResponseStatusException(HttpStatus.NOT_FOUND)

        request.category?.let { item.category = it }
        request.amount?.let { item.amount = it }
        request.memo?.let { item.memo = it }

        return BudgetItemResponse.of(item)
    }

    fun delete(tripId: UUID, itemId: UUID, userId: UUID) {
        requireEditorOrAbove(tripId, userId)
        val item = budgetRepository.findByIdOrNull(itemId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (item.trip.id != tripId) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        budgetRepository.delete(item)
    }

    private fun requireMember(tripId: UUID, userId: UUID) {
        tripMemberRepository.findByTripIdAndUserId(tripId, userId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    private fun requireEditorOrAbove(tripId: UUID, userId: UUID) {
        val member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
        if (member.role == TripRole.VIEWER) throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }
}
