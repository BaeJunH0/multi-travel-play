package demo.travel.budget

import demo.travel.budget.application.BudgetService
import demo.travel.budget.application.dto.BudgetCommand
import demo.travel.common.TripCategory
import demo.travel.trip.Trip
import demo.travel.trip.TripMember
import demo.travel.trip.TripMemberRepository
import demo.travel.trip.TripRepository
import demo.travel.trip.TripRole
import demo.travel.user.AuthProvider
import demo.travel.user.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.*

class BudgetServiceTest : BehaviorSpec({

    val budgetRepository = mockk<BudgetRepository>()
    val tripRepository = mockk<TripRepository>()
    val tripMemberRepository = mockk<TripMemberRepository>()
    val service = BudgetService(budgetRepository, tripRepository, tripMemberRepository)

    val userId = UUID.randomUUID()
    val tripId = UUID.randomUUID()
    val itemId = UUID.randomUUID()
    val user = User(id = userId, email = "user@test.com", nickname = "유저", provider = AuthProvider.LOCAL)
    val trip = Trip(
        id = tripId, owner = user, title = "여행", destination = "서울",
        startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 3),
    )

    fun memberWith(role: TripRole) = TripMember(trip = trip, user = user, role = role)
    fun makeItem(t: Trip = trip) = BudgetItem(id = itemId, trip = t, category = TripCategory.FOOD, amount = 30000)

    beforeEach { clearAllMocks() }

    given("getItems") {
        `when`("VIEWER 이상의 멤버일 때") {
            then("예산 항목 목록을 반환한다") {
                val items = listOf(makeItem(), makeItem())
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.VIEWER)
                every { budgetRepository.findAllByTripId(tripId) } returns items

                val result = service.getItems(tripId, userId)
                result shouldHaveSize 2
            }
        }

        `when`("멤버가 아닐 때") {
            then("403 FORBIDDEN을 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns null

                val ex = shouldThrow<ResponseStatusException> { service.getItems(tripId, userId) }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }
    }

    given("create") {
        val command = BudgetCommand.Create(tripId = tripId, category = TripCategory.HOTEL, amount = 100000, memo = "숙박비", userId = userId)

        `when`("EDITOR 권한일 때") {
            then("예산 항목을 저장하고 반환한다") {
                val savedItem = BudgetItem(trip = trip, category = TripCategory.HOTEL, amount = 100000, memo = "숙박비")
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { tripRepository.findByIdOrNull(tripId) } returns trip
                every { budgetRepository.save(any()) } returns savedItem

                val result = service.create(command)
                result.category shouldBe TripCategory.HOTEL
                result.amount shouldBe 100000
            }
        }

        `when`("VIEWER 권한일 때") {
            then("403 FORBIDDEN을 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.VIEWER)

                val ex = shouldThrow<ResponseStatusException> { service.create(command) }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }

        `when`("여행이 존재하지 않을 때") {
            then("404 NOT_FOUND를 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { tripRepository.findByIdOrNull(tripId) } returns null

                val ex = shouldThrow<ResponseStatusException> { service.create(command) }
                ex.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    }

    given("update") {
        val command = BudgetCommand.Update(tripId = tripId, itemId = itemId, category = TripCategory.TRANSPORT, amount = 50000, memo = null, userId = userId)

        `when`("EDITOR 권한이고 해당 여행의 항목일 때") {
            then("항목을 수정하고 반환한다") {
                val item = makeItem()
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { budgetRepository.findByIdOrNull(itemId) } returns item

                val result = service.update(command)
                result.category shouldBe TripCategory.TRANSPORT
                result.amount shouldBe 50000
            }
        }

        `when`("다른 여행에 속한 항목일 때") {
            then("404 NOT_FOUND를 던진다") {
                val otherTrip = Trip(
                    owner = user, title = "다른 여행", destination = "부산",
                    startDate = LocalDate.of(2026, 9, 1), endDate = LocalDate.of(2026, 9, 3),
                )
                val item = makeItem(t = otherTrip)  // 다른 trip 소속
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { budgetRepository.findByIdOrNull(itemId) } returns item

                val ex = shouldThrow<ResponseStatusException> { service.update(command) }
                ex.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }

        `when`("VIEWER 권한일 때") {
            then("403 FORBIDDEN을 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.VIEWER)

                val ex = shouldThrow<ResponseStatusException> { service.update(command) }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }
    }

    given("delete") {
        val command = BudgetCommand.Delete(tripId = tripId, itemId = itemId, userId = userId)

        `when`("EDITOR 권한이고 해당 여행의 항목일 때") {
            then("항목을 삭제한다") {
                val item = makeItem()
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { budgetRepository.findByIdOrNull(itemId) } returns item
                every { budgetRepository.delete(item) } just Runs

                service.delete(command)
                verify { budgetRepository.delete(item) }
            }
        }

        `when`("항목이 존재하지 않을 때") {
            then("404 NOT_FOUND를 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { budgetRepository.findByIdOrNull(itemId) } returns null

                val ex = shouldThrow<ResponseStatusException> { service.delete(command) }
                ex.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    }
})
