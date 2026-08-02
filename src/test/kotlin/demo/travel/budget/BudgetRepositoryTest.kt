package demo.travel.budget

import demo.travel.common.TripCategory
import demo.travel.trip.Trip
import demo.travel.trip.TripRepository
import demo.travel.user.AuthProvider
import demo.travel.user.User
import demo.travel.user.UserRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class BudgetRepositoryTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var tripRepository: TripRepository
    @Autowired lateinit var budgetRepository: BudgetRepository

    private fun savedUser(suffix: String) =
        userRepository.save(User(email = "bdr-$suffix@test.com", nickname = "유저$suffix", provider = AuthProvider.LOCAL))

    private fun savedTrip(owner: User) = tripRepository.save(
        Trip(
            owner = owner, title = "여행", destination = "서울",
            startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 3),
        )
    )

    private fun savedItem(trip: Trip, category: TripCategory = TripCategory.ETC, amount: Int = 10000) =
        budgetRepository.save(BudgetItem(trip = trip, category = category, amount = amount))

    init {
        given("findAllByTripId") {
            `when`("해당 여행에 예산 항목이 3개 있을 때") {
                then("3개를 반환한다") {
                    val user = savedUser("fa1")
                    val trip = savedTrip(user)
                    savedItem(trip, TripCategory.FLIGHT, 500000)
                    savedItem(trip, TripCategory.HOTEL, 300000)
                    savedItem(trip, TripCategory.FOOD, 50000)

                    val result = budgetRepository.findAllByTripId(trip.id)

                    result shouldHaveSize 3
                }
            }

            `when`("해당 여행에 예산 항목이 없을 때") {
                then("빈 리스트를 반환한다") {
                    val user = savedUser("fa2")
                    val trip = savedTrip(user)

                    val result = budgetRepository.findAllByTripId(trip.id)

                    result shouldHaveSize 0
                }
            }

            `when`("다른 여행의 예산 항목이 함께 존재할 때") {
                then("해당 여행의 항목만 반환한다") {
                    val user = savedUser("fa3")
                    val trip1 = savedTrip(user)
                    val trip2 = tripRepository.save(
                        Trip(
                            owner = user, title = "다른 여행", destination = "부산",
                            startDate = LocalDate.of(2026, 9, 1), endDate = LocalDate.of(2026, 9, 2),
                        )
                    )
                    savedItem(trip1, TripCategory.TRANSPORT, 20000)
                    savedItem(trip2, TripCategory.ETC, 5000)

                    val result = budgetRepository.findAllByTripId(trip1.id)

                    result shouldHaveSize 1
                    result[0].trip.id shouldBe trip1.id
                }
            }

            `when`("카테고리와 금액이 정확히 저장됐는지 확인") {
                then("저장한 category와 amount를 반환한다") {
                    val user = savedUser("fa4")
                    val trip = savedTrip(user)
                    savedItem(trip, TripCategory.HOTEL, 150000)

                    val result = budgetRepository.findAllByTripId(trip.id)

                    result shouldHaveSize 1
                    result[0].category shouldBe TripCategory.HOTEL
                    result[0].amount shouldBe 150000
                }
            }
        }
    }
}
