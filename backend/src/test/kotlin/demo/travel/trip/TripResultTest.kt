package demo.travel.trip

import demo.travel.trip.application.dto.TripResult
import demo.travel.user.AuthProvider
import demo.travel.user.User
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.util.*

class TripResultTest : BehaviorSpec({

    val user = User(email = "owner@test.com", nickname = "오너", provider = AuthProvider.LOCAL)

    fun makeTrip(startDate: LocalDate, endDate: LocalDate) = Trip(
        owner = user, title = "여행", destination = "서울",
        startDate = startDate, endDate = endDate,
    )

    given("TripResult.Detail.of") {
        `when`("3박4일 여행 (8/1 ~ 8/4)") {
            then("days 리스트가 4개이고 dayNumber와 date가 올바르다") {
                val trip = makeTrip(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 4))

                val result = TripResult.Detail.of(trip, TripRole.OWNER, memberCount = 1)

                result.days shouldHaveSize 4
                result.days[0].dayNumber shouldBe 1
                result.days[0].date shouldBe LocalDate.of(2026, 8, 1)
                result.days[3].dayNumber shouldBe 4
                result.days[3].date shouldBe LocalDate.of(2026, 8, 4)
            }
        }

        `when`("당일치기 여행 (startDate == endDate)") {
            then("days 리스트가 1개다") {
                val trip = makeTrip(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1))

                val result = TripResult.Detail.of(trip, TripRole.VIEWER, memberCount = 2)

                result.days shouldHaveSize 1
                result.days[0].dayNumber shouldBe 1
                result.days[0].date shouldBe LocalDate.of(2026, 8, 1)
            }
        }

        `when`("응답 필드 전체 매핑 확인") {
            then("trip의 id, title, destination, startDate, endDate, myRole, memberCount가 결과에 반영된다") {
                val trip = makeTrip(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2))

                val result = TripResult.Detail.of(trip, TripRole.EDITOR, memberCount = 3)

                result.id shouldBe trip.id
                result.title shouldBe "여행"
                result.destination shouldBe "서울"
                result.startDate shouldBe LocalDate.of(2026, 8, 1)
                result.endDate shouldBe LocalDate.of(2026, 8, 2)
                result.myRole shouldBe TripRole.EDITOR
                result.memberCount shouldBe 3
            }
        }
    }
})
