package demo.travel.common

import demo.travel.block.BlockType
import demo.travel.block.dto.BlockResponse
import demo.travel.common.exception.BusinessException
import demo.travel.common.exception.VersionConflictException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus
import java.util.*

class ExceptionTest : BehaviorSpec({

    given("BusinessException") {
        `when`("code, message만 전달하면") {
            then("status 기본값은 400 BAD_REQUEST이다") {
                val ex = BusinessException(code = "SOME_ERROR", message = "잘못된 요청")

                ex.code shouldBe "SOME_ERROR"
                ex.message shouldBe "잘못된 요청"
                ex.status shouldBe HttpStatus.BAD_REQUEST
            }
        }

        `when`("status를 명시적으로 지정하면") {
            then("지정한 HttpStatus를 반환한다") {
                val ex = BusinessException(code = "FORBIDDEN_OP", message = "권한 없음", status = HttpStatus.FORBIDDEN)

                ex.status shouldBe HttpStatus.FORBIDDEN
                ex.code shouldBe "FORBIDDEN_OP"
            }
        }

        `when`("404 NOT_FOUND 상태로 생성하면") {
            then("status가 NOT_FOUND이다") {
                val ex = BusinessException(code = "NOT_FOUND", message = "리소스 없음", status = HttpStatus.NOT_FOUND)

                ex.status shouldBe HttpStatus.NOT_FOUND
            }
        }
    }

    given("VersionConflictException") {
        val blockResponse = BlockResponse(
            id = UUID.randomUUID(),
            dayNumber = 1, position = 1.0,
            blockType = BlockType.PLACE, placeName = "경복궁",
            lat = null, lng = null, startTime = null,
            durationMin = null, cost = null, memo = null,
            lockedBy = null, lockedByNickname = null,
            version = 3L,
        )

        `when`("현재 블록 상태와 함께 생성하면") {
            then("메시지가 고정 문자열이고 currentBlock이 포함된다") {
                val ex = VersionConflictException(blockResponse)

                ex.message shouldBe "다른 사용자가 이미 수정했습니다."
                ex.currentBlock shouldBe blockResponse
                ex.currentBlock.version shouldBe 3L
            }
        }

        `when`("RuntimeException을 상속하는지 확인") {
            then("RuntimeException의 인스턴스이다") {
                val ex = VersionConflictException(blockResponse)

                (ex is RuntimeException) shouldBe true
            }
        }
    }
})
