package demo.travel.budget

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BudgetRepository : JpaRepository<BudgetItem, UUID> {
    fun findAllByTripId(tripId: UUID): List<BudgetItem>
    fun findByBlockId(blockId: UUID): BudgetItem?
}
