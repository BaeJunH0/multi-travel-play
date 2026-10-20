package demo.travel.ai

import demo.travel.ai.client.AnthropicClient
import demo.travel.ai.client.AnthropicProperties
import demo.travel.common.TripCategory
import demo.travel.common.exception.BusinessException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.net.http.HttpTimeoutException

class AnthropicClientTest :
    BehaviorSpec({

        val objectMapper = jacksonObjectMapper()
        val props = mockk<AnthropicProperties>()
        every { props.apiKey } returns "test-key"
        every { props.model } returns "claude-sonnet-5"
        val client = AnthropicClient(props, objectMapper)

        val blocksJson =
            """{"blocks":[{"tempId":"temp-1","blockType":"FOOD","placeName":"식당","startTime":"12:00",""" +
                """"durationMin":60,"cost":10000,"suggestedDay":1,"memo":null}]}"""
        val thinkingBlock = """{"type":"thinking","thinking":"생각 중...","signature":"sig"}"""

        fun textBlock(text: String) = """{"type":"text","text":${objectMapper.writeValueAsString(text)}}"""
        fun response(stopReason: String, vararg content: String) =
            """{"id":"msg_1","type":"message","role":"assistant","content":[${content.joinToString(",")}],"stop_reason":"$stopReason"}"""

        given("parse") {
            `when`("content가 thinking 블록으로 시작하고 그 뒤에 text 블록이 있을 때") {
                then("text 블록의 JSON을 파싱한다") {
                    val result = client.parse(response("end_turn", thinkingBlock, textBlock(blocksJson)))

                    result.size shouldBe 1
                    result.first().tempId shouldBe "temp-1"
                    result.first().blockType shouldBe TripCategory.FOOD
                    result.first().suggestedDay shouldBe 1
                }
            }

            `when`("content에 text 블록만 있을 때") {
                then("text 블록의 JSON을 파싱한다") {
                    val result = client.parse(response("end_turn", textBlock(blocksJson)))

                    result.map { it.placeName } shouldBe listOf("식당")
                }
            }

            `when`("text가 마크다운 코드블록으로 감싸져 있을 때") {
                then("코드블록 표시를 벗겨내고 파싱한다") {
                    val result = client.parse(response("end_turn", textBlock("```json\n$blocksJson\n```")))

                    result.map { it.tempId } shouldBe listOf("temp-1")
                }
            }

            `when`("stop_reason이 max_tokens일 때") {
                then("502 AI_RESPONSE_INVALID를 던진다") {
                    val ex = shouldThrow<BusinessException> {
                        client.parse(response("max_tokens", textBlock("""{"blocks":[{"tempId":"temp-1""")))
                    }
                    ex.code shouldBe "AI_RESPONSE_INVALID"
                    ex.status shouldBe HttpStatus.BAD_GATEWAY
                }
            }

            `when`("text 블록이 없을 때") {
                then("502 AI_RESPONSE_INVALID를 던진다") {
                    val ex = shouldThrow<BusinessException> { client.parse(response("end_turn", thinkingBlock)) }
                    ex.code shouldBe "AI_RESPONSE_INVALID"
                    ex.status shouldBe HttpStatus.BAD_GATEWAY
                }
            }

            `when`("text가 올바른 JSON이 아닐 때") {
                then("502 AI_RESPONSE_INVALID를 던진다") {
                    val ex = shouldThrow<BusinessException> { client.parse(response("end_turn", textBlock("일정을 만들 수 없습니다."))) }
                    ex.code shouldBe "AI_RESPONSE_INVALID"
                    ex.status shouldBe HttpStatus.BAD_GATEWAY
                }
            }
        }

        given("toAiException") {
            fun apiError(status: Int) = RestClientResponseException(
                "error",
                HttpStatusCode.valueOf(status),
                "error",
                null,
                """{"type":"error","error":{"type":"some_error","message":"msg"}}""".toByteArray(),
                Charsets.UTF_8,
            )

            `when`("429·500·529처럼 다시 시도하면 되는 응답일 때") {
                then("503 AI_UNAVAILABLE로 바꾼다") {
                    listOf(429, 500, 529).forEach { status ->
                        val ex = client.toAiException(apiError(status))
                        ex.code shouldBe "AI_UNAVAILABLE"
                        ex.status shouldBe HttpStatus.SERVICE_UNAVAILABLE
                    }
                }
            }

            `when`("400·401·404처럼 키·모델·요청 문제일 때") {
                then("502 AI_REQUEST_FAILED로 바꾼다") {
                    listOf(400, 401, 404).forEach { status ->
                        val ex = client.toAiException(apiError(status))
                        ex.code shouldBe "AI_REQUEST_FAILED"
                        ex.status shouldBe HttpStatus.BAD_GATEWAY
                    }
                }
            }

            `when`("timeout·연결 실패일 때") {
                then("503 AI_UNAVAILABLE로 바꾼다") {
                    val ex = client.toAiException(ResourceAccessException("timeout", HttpTimeoutException("timed out")))
                    ex.code shouldBe "AI_UNAVAILABLE"
                    ex.status shouldBe HttpStatus.SERVICE_UNAVAILABLE
                }
            }
        }
    })
