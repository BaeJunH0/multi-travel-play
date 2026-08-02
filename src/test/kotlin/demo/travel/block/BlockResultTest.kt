package demo.travel.block

import demo.travel.block.application.dto.BlockResult
import demo.travel.trip.Trip
import demo.travel.user.AuthProvider
import demo.travel.user.User
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalTime

class BlockResultTest : BehaviorSpec({

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

    given("BlockResult.of") {
        `when`("잠금이 없는 블록") {
            then("lockedBy와 lockedByNickname이 null이다") {
                val block = makeBlock(lockedBy = null)

                val result = BlockResult.of(block)

                result.lockedBy shouldBe null
                result.lockedByNickname shouldBe null
            }
        }

        `when`("다른 사용자가 잠근 블록") {
            then("lockedBy는 잠금자 UUID, lockedByNickname은 잠금자 닉네임이다") {
                val block = makeBlock(lockedBy = locker)

                val result = BlockResult.of(block)

                result.lockedBy shouldBe locker.id
                result.lockedByNickname shouldBe "잠금자"
            }
        }

        `when`("startTime이 있는 블록") {
            then("startTime이 문자열로 변환된다") {
                val block = makeBlock(startTime = LocalTime.of(9, 30))

                val result = BlockResult.of(block)

                result.startTime shouldBe "09:30"
            }
        }

        `when`("startTime이 없는 블록") {
            then("startTime이 null이다") {
                val block = makeBlock(startTime = null)

                val result = BlockResult.of(block)

                result.startTime shouldBe null
            }
        }

        `when`("전체 필드 매핑 확인") {
            then("블록의 모든 필드가 결과에 정확히 반영된다") {
                val block = makeBlock(cost = 5000, memo = "입장료", startTime = LocalTime.of(14, 0))

                val result = BlockResult.of(block)

                result.id shouldBe block.id
                result.dayNumber shouldBe 1
                result.position shouldBe 1.0
                result.blockType shouldBe BlockType.PLACE
                result.placeName shouldBe "경복궁"
                result.cost shouldBe 5000
                result.memo shouldBe "입장료"
                result.version shouldBe block.version
            }
        }
    }
})
