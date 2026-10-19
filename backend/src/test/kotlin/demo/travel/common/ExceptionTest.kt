package demo.travel.common

import demo.travel.common.exception.BusinessException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus

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
})
