package demo.travel.common

import demo.travel.common.exception.GlobalExceptionHandler
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException

class GlobalExceptionHandlerTest : BehaviorSpec({

    given("HttpMessageNotReadableException") {
        `when`("요청 바디 파싱에 실패하면") {
            then("400과 표준 ErrorResponse 형태로 응답한다") {
                val handler = GlobalExceptionHandler()
                val ex = HttpMessageNotReadableException("field missing", mockk<HttpInputMessage>(relaxed = true))

                val response = handler.handleMessageNotReadable(ex)

                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                response.body?.code shouldBe "INVALID_REQUEST"
                response.body?.message shouldBe "요청 본문이 올바르지 않습니다."
            }
        }
    }
})
