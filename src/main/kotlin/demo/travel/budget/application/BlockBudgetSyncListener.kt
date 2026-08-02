package demo.travel.budget.application

import demo.travel.block.event.BlockCostChangedEvent
import demo.travel.block.event.BlockDeletedEvent
import demo.travel.budget.BudgetItem
import demo.travel.budget.BudgetRepository
import demo.travel.trip.TripRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class BlockBudgetSyncListener(
    private val budgetRepository: BudgetRepository,
    private val tripRepository: TripRepository,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onCostChanged(event: BlockCostChangedEvent) {
        val existing = budgetRepository.findByBlockId(event.blockId)
        if (existing != null) {
            existing.category = event.category
            existing.amount = event.cost
            existing.memo = event.placeName
        } else {
            budgetRepository.save(
                BudgetItem(
                    trip = tripRepository.getReferenceById(event.tripId),
                    blockId = event.blockId,
                    category = event.category,
                    amount = event.cost,
                    memo = event.placeName,
                )
            )
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onBlockDeleted(event: BlockDeletedEvent) {
        budgetRepository.findByBlockId(event.blockId)?.let { budgetRepository.delete(it) }
    }
}
