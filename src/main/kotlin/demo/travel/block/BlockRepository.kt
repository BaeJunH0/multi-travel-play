package demo.travel.block

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface BlockRepository : JpaRepository<ScheduleBlock, UUID> {
    fun findAllByTripIdOrderByDayNumberAscPositionAsc(tripId: UUID): List<ScheduleBlock>
    fun findTopByTripIdAndDayNumberOrderByPositionDesc(tripId: UUID, dayNumber: Int): ScheduleBlock?
}
