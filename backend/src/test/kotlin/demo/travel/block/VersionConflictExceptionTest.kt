package demo.travel.block

import demo.travel.block.application.VersionConflictException
import demo.travel.block.application.dto.BlockResult
import demo.travel.block.presentation.BlockExceptionHandler
import demo.travel.common.TripCategory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus
import java.util.*

class VersionConflictExceptionTest :
    BehaviorSpec({

        val blockResult = BlockResult(
            id = UUID.randomUUID(),
            dayNumber = 1, position = 1.0,
            blockType = TripCategory.PLACE, placeName = "경복궁",
            lat = null, lng = null, startTime = null,
            durationMin = null, cost = null, memo = null,
            lockedBy = null, lockedByNickname = null,
            version = 3L,
        )

        given("VersionConflictException") {
            `when`("현재 블록 상태와 함께 생성하면") {
                then("메시지가 고정 문자열이고 currentBlock이 포함된다") {
                    val ex = VersionConflictException(blockResult)

                    ex.message shouldBe "다른 사용자가 이미 수정했습니다."
                    ex.currentBlock shouldBe blockResult
                    ex.currentBlock.version shouldBe 3L
                }
            }

            `when`("RuntimeException을 상속하는지 확인") {
                then("RuntimeException의 인스턴스이다") {
                    val ex = VersionConflictException(blockResult)

                    (ex is RuntimeException) shouldBe true
                }
            }
        }

        given("BlockExceptionHandler") {
            `when`("VersionConflictException을 처리하면") {
                then("409와 code·message·currentBlock을 응답한다") {
                    val response = BlockExceptionHandler().handleVersionConflict(VersionConflictException(blockResult))

                    response.statusCode shouldBe HttpStatus.CONFLICT
                    response.body?.code shouldBe "VERSION_CONFLICT"
                    response.body?.message shouldBe "다른 사용자가 이미 수정했습니다."
                    response.body?.currentBlock?.id shouldBe blockResult.id
                    response.body?.currentBlock?.version shouldBe 3L
                }
            }
        }
    })
