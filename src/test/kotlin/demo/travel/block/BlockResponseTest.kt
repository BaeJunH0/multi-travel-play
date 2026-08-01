package demo.travel.block

import demo.travel.block.dto.BlockResponse
import demo.travel.trip.Trip
import demo.travel.user.AuthProvider
import demo.travel.user.User
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalTime

class BlockResponseTest : BehaviorSpec({

    val creator = User(email = "creator@test.com", nickname = "생성자", provider = AuthProvider.LOCAL)
    val locker = User(email = "locker@test.com", nickname = "잠금자", provider = AuthProvider.LOCAL)
    val trip = Trip(
        owner = creator, title = "여행", destination = "서울",
        startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 3),
    )

    fun makeBlock(
        lockedBy: User? = null,
        startTime: LocalTime? = null,
        cost: Int? = null,
        memo: String? = null,
    ) = ScheduleBlock(
        trip = trip, dayNumber = 1, position = 1.0,
        blockType = BlockType.PLACE, placeName = "경복궁",
        createdBy = creator, startTime = startTime, cost = cost, memo = memo, lockedBy = lockedBy,
    )

    given("BlockResponse.of") {
        `when`("잠금이 없는 블록") {
            then("lockedBy와 lockedByNickname이 null이다") {
                val block = makeBlock(lockedBy = null)

                val response = BlockResponse.of(block)

                response.lockedBy shouldBe null
                response.lockedByNickname shouldBe null
            }
        }

        `when`("다른 사용자가 잠근 블록") {
            then("lockedBy는 잠금자 UUID, lockedByNickname은 잠금자 닉네임이다") {
                val block = makeBlock(lockedBy = locker)

                val response = BlockResponse.of(block)

                response.lockedBy shouldBe locker.id
                response.lockedByNickname shouldBe "잠금자"
            }
        }

        `when`("startTime이 있는 블록") {
            then("startTime이 문자열로 변환된다") {
                val block = makeBlock(startTime = LocalTime.of(9, 30))

                val response = BlockResponse.of(block)

                response.startTime shouldBe "09:30"
            }
        }

        `when`("startTime이 없는 블록") {
            then("startTime이 null이다") {
                val block = makeBlock(startTime = null)

                val response = BlockResponse.of(block)

                response.startTime shouldBe null
            }
        }

        `when`("전체 필드 매핑 확인") {
            then("블록의 모든 필드가 응답에 정확히 반영된다") {
                val block = makeBlock(cost = 5000, memo = "입장료", startTime = LocalTime.of(14, 0))

                val response = BlockResponse.of(block)

                response.id shouldBe block.id
                response.dayNumber shouldBe 1
                response.position shouldBe 1.0
                response.blockType shouldBe BlockType.PLACE
                response.placeName shouldBe "경복궁"
                response.cost shouldBe 5000
                response.memo shouldBe "입장료"
                response.version shouldBe block.version
            }
        }
    }
})
