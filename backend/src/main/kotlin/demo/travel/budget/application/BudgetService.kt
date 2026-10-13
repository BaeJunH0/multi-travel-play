package demo.travel.budget.application

import demo.travel.budget.BudgetItem
import demo.travel.budget.BudgetRepository
import demo.travel.budget.application.dto.BudgetCommand
import demo.travel.budget.application.dto.BudgetResult
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
    fun getItems(tripId: UUID, userId: UUID): List<BudgetResult> {
        requireMember(tripId, userId)
        return budgetRepository.findAllByTripId(tripId).map { BudgetResult.of(it) }
    }

    fun create(command: BudgetCommand.Create): BudgetResult {
        requireEditorOrAbove(command.tripId, command.userId)
        val trip = tripRepository.findByIdOrNull(command.tripId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val item = budgetRepository.save(
            BudgetItem(trip = trip, category = command.category, amount = command.amount, memo = command.memo)
        )
        return BudgetResult.of(item)
    }

    fun update(command: BudgetCommand.Update): BudgetResult {
        requireEditorOrAbove(command.tripId, command.userId)
        val item = budgetRepository.findByIdOrNull(command.itemId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (item.trip.id != command.tripId) throw ResponseStatusException(HttpStatus.NOT_FOUND)

        command.category?.let { item.category = it }
        command.amount?.let { item.amount = it }
        command.memo?.let { item.memo = it }

        return BudgetResult.of(item)
    }

    fun delete(command: BudgetCommand.Delete) {
        requireEditorOrAbove(command.tripId, command.userId)
        val item = budgetRepository.findByIdOrNull(command.itemId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (item.trip.id != command.tripId) throw ResponseStatusException(HttpStatus.NOT_FOUND)
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
