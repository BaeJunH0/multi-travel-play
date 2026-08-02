package demo.travel.budget

import demo.travel.block.event.BlockCostChangedEvent
import demo.travel.block.event.BlockDeletedEvent
import demo.travel.budget.application.BlockBudgetSyncListener
import demo.travel.common.TripCategory
import demo.travel.trip.Trip
import demo.travel.trip.TripRepository
import demo.travel.user.AuthProvider
import demo.travel.user.User
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.time.LocalDate
import java.util.*

class BlockBudgetSyncListenerTest : BehaviorSpec({

    val budgetRepository = mockk<BudgetRepository>()
    val tripRepository = mockk<TripRepository>()
    val listener = BlockBudgetSyncListener(budgetRepository, tripRepository)

    val tripId = UUID.randomUUID()
    val blockId = UUID.randomUUID()
    val owner = User(email = "owner@test.com", nickname = "주인", provider = AuthProvider.LOCAL)
    val trip = Trip(
        id = tripId, owner = owner, title = "여행", destination = "서울",
        startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 3),
    )

    beforeEach { clearAllMocks() }

    given("onCostChanged") {
        val event = BlockCostChangedEvent(
            blockId = blockId, tripId = tripId, category = TripCategory.FOOD, cost = 30000, placeName = "맛집",
        )

        `when`("연동된 BudgetItem이 없을 때") {
            then("blockId로 연결된 새 BudgetItem을 생성한다") {
                every { budgetRepository.findByBlockId(blockId) } returns null
                every { tripRepository.getReferenceById(tripId) } returns trip
                every { budgetRepository.save(any()) } answers { firstArg() }

                listener.onCostChanged(event)

                verify {
                    budgetRepository.save(match {
                        it.blockId == blockId && it.category == TripCategory.FOOD && it.amount == 30000 && it.memo == "맛집"
                    })
                }
            }
        }

        `when`("연동된 BudgetItem이 이미 있을 때") {
            then("기존 항목의 category/amount/memo를 갱신한다") {
                val existing = BudgetItem(trip = trip, blockId = blockId, category = TripCategory.ETC, amount = 1000, memo = "이전")
                every { budgetRepository.findByBlockId(blockId) } returns existing

                listener.onCostChanged(event)

                existing.category shouldBe TripCategory.FOOD
                existing.amount shouldBe 30000
                existing.memo shouldBe "맛집"
                verify(exactly = 0) { budgetRepository.save(any()) }
            }
        }
    }

    given("onBlockDeleted") {
        val event = BlockDeletedEvent(blockId)

        `when`("연동된 BudgetItem이 있을 때") {
            then("해당 BudgetItem을 삭제한다") {
                val existing = BudgetItem(trip = trip, blockId = blockId, category = TripCategory.FOOD, amount = 30000)
                every { budgetRepository.findByBlockId(blockId) } returns existing
                every { budgetRepository.delete(existing) } just Runs

                listener.onBlockDeleted(event)

                verify { budgetRepository.delete(existing) }
            }
        }

        `when`("연동된 BudgetItem이 없을 때") {
            then("아무 일도 일어나지 않는다") {
                every { budgetRepository.findByBlockId(blockId) } returns null

                listener.onBlockDeleted(event)

                verify(exactly = 0) { budgetRepository.delete(any()) }
            }
        }
    }
})
