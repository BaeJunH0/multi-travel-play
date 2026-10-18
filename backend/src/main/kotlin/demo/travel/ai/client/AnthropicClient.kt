package demo.travel.ai.client

import com.fasterxml.jackson.annotation.JsonProperty
import demo.travel.ai.client.dto.AiBlock
import demo.travel.common.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.net.http.HttpClient
import java.time.Duration

@Component
class AnthropicClient(
    private val props: AnthropicProperties,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(AnthropicClient::class.java)

    private val restClient = RestClient.builder()
        .baseUrl("https://api.anthropic.com")
        .defaultHeader("x-api-key", props.apiKey)
        .defaultHeader("anthropic-version", "2023-06-01")
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
            ).apply { setReadTimeout(READ_TIMEOUT) }
        )
        .build()

    fun generate(userPrompt: String): List<AiBlock> {
        val body = mapOf(
            "model" to props.model,
            "max_tokens" to MAX_TOKENS,
            "system" to SYSTEM_PROMPT,
            "messages" to listOf(
                mapOf("role" to "user", "content" to userPrompt),
            ),
        )

        val responseBody = try {
            restClient.post()
                .uri("/v1/messages")
                .body(body)
                .retrieve()
                .body(String::class.java)!!
        } catch (e: RestClientResponseException) {
            throw toAiException(e)
        } catch (e: ResourceAccessException) {
            throw toAiException(e)
        }

        return parse(responseBody)
    }

    /**
     * Anthropic 호출 실패를 표준 에러로 바꾼다.
     * 429·5xx(529 overloaded 포함)·timeout·연결 실패는 다시 시도하면 되므로 503, 그 외 4xx는 키·모델·요청 문제라 502.
     */
    internal fun toAiException(e: Exception): BusinessException {
        if (e is RestClientResponseException) {
            val status = e.statusCode.value()
            if (status == 429 || status >= 500) {
                log.warn("Anthropic API 일시 오류: status={}, body={}", status, e.responseBodyAsString)
                return unavailable()
            }
            log.error("Anthropic API 요청 실패: status={}, body={}", status, e.responseBodyAsString)
            return BusinessException("AI_REQUEST_FAILED", "AI 일정 생성 요청에 실패했습니다. 관리자에게 문의해 주세요.", HttpStatus.BAD_GATEWAY)
        }
        log.warn("Anthropic API 연결 실패 또는 시간 초과", e)
        return unavailable()
    }

    private fun unavailable() =
        BusinessException("AI_UNAVAILABLE", "AI 서비스가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.SERVICE_UNAVAILABLE)

    /**
     * Messages API 응답 본문에서 일정 블록을 꺼낸다.
     * adaptive thinking이 켜져 있으면 content가 thinking 블록으로 시작할 수 있어서 type이 text인 블록을 고른다.
     */
    internal fun parse(responseBody: String): List<AiBlock> {
        try {
            val response = objectMapper.readValue<MessagesResponse>(responseBody)
            if (response.stopReason != "end_turn") {
                throw invalidResponse("AI 응답이 완료되지 않았습니다 (stop_reason: ${response.stopReason}). 다시 시도해 주세요.")
            }
            val text = response.content.firstOrNull { it.type == "text" }?.text
                ?: throw invalidResponse("AI 응답에 텍스트가 없습니다. 다시 시도해 주세요.")

            val json = text
                .trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            return objectMapper.readValue<BlocksPayload>(json).blocks.map { b ->
                AiBlock(
                    tempId = b.tempId,
                    blockType = enumValueOf(b.blockType),
                    placeName = b.placeName,
                    startTime = b.startTime,
                    durationMin = b.durationMin,
                    cost = b.cost,
                    suggestedDay = b.suggestedDay,
                    memo = b.memo,
                )
            }
        } catch (e: JacksonException) {
            throw invalidResponse("AI 응답을 해석하지 못했습니다. 다시 시도해 주세요.")
        } catch (e: IllegalArgumentException) {
            // 알 수 없는 blockType (enumValueOf 실패)
            throw invalidResponse("AI 응답을 해석하지 못했습니다. 다시 시도해 주세요.")
        }
    }

    private fun invalidResponse(message: String) =
        BusinessException("AI_RESPONSE_INVALID", message, HttpStatus.BAD_GATEWAY)

    private data class MessagesResponse(
        val content: List<ContentBlock>,
        @JsonProperty("stop_reason") val stopReason: String?,
    )
    private data class ContentBlock(val type: String, val text: String?)

    private data class BlocksPayload(val blocks: List<RawBlock>)
    private data class RawBlock(
        val tempId: String,
        val blockType: String,
        val placeName: String,
        val startTime: String?,
        val durationMin: Int?,
        val cost: Int?,
        val suggestedDay: Int,
        val memo: String?,
    )

    companion object {
        // adaptive thinking이 출력과 한도를 나눠 쓰므로 넉넉하게 둔다 (비스트리밍 권장값)
        private const val MAX_TOKENS = 16000
        private val READ_TIMEOUT: Duration = Duration.ofSeconds(120)

        private val SYSTEM_PROMPT = """
            당신은 여행 일정 전문가입니다.
            사용자의 조건에 맞는 여행 일정 블록을 생성하고,
            반드시 아래 JSON 형식만 반환하세요.
            마크다운 코드블록, 설명 텍스트 없이 순수 JSON만 출력하세요.
        """.trimIndent()
    }
}
