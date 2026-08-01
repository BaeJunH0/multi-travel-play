package demo.travel.trip

import demo.travel.trip.dto.TripDetailResponse
import demo.travel.user.AuthProvider
import demo.travel.user.User
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.util.*

class TripDetailResponseTest : BehaviorSpec({

    val user = User(email = "owner@test.com", nickname = "오너", provider = AuthProvider.LOCAL)

    fun makeTrip(startDate: LocalDate, endDate: LocalDate) = Trip(
        owner = user, title = "여행", destination = "서울",
        startDate = startDate, endDate = endDate,
    )

    given("TripDetailResponse.of") {
        `when`("3박4일 여행 (8/1 ~ 8/4)") {
            then("days 리스트가 4개이고 dayNumber와 date가 올바르다") {
                val trip = makeTrip(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 4))

                val response = TripDetailResponse.of(trip, TripRole.OWNER, memberCount = 1)

                response.days shouldHaveSize 4
                response.days[0].dayNumber shouldBe 1
                response.days[0].date shouldBe LocalDate.of(2026, 8, 1)
                response.days[3].dayNumber shouldBe 4
                response.days[3].date shouldBe LocalDate.of(2026, 8, 4)
            }
        }

        `when`("당일치기 여행 (startDate == endDate)") {
            then("days 리스트가 1개다") {
                val trip = makeTrip(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1))

                val response = TripDetailResponse.of(trip, TripRole.VIEWER, memberCount = 2)

                response.days shouldHaveSize 1
                response.days[0].dayNumber shouldBe 1
                response.days[0].date shouldBe LocalDate.of(2026, 8, 1)
            }
        }

        `when`("응답 필드 전체 매핑 확인") {
            then("trip의 id, title, destination, startDate, endDate, myRole, memberCount가 응답에 반영된다") {
                val trip = makeTrip(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2))

                val response = TripDetailResponse.of(trip, TripRole.EDITOR, memberCount = 3)

                response.id shouldBe trip.id
                response.title shouldBe "여행"
                response.destination shouldBe "서울"
                response.startDate shouldBe LocalDate.of(2026, 8, 1)
                response.endDate shouldBe LocalDate.of(2026, 8, 2)
                response.myRole shouldBe TripRole.EDITOR
                response.memberCount shouldBe 3
            }
        }
    }
})
