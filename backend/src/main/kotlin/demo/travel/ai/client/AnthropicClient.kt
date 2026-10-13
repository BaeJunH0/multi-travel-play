package demo.travel.ai.client

import demo.travel.ai.client.properties.AnthropicProperties
import demo.travel.ai.presentation.dto.AiBlock
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.net.http.HttpClient
import java.time.Duration

@Component
class AnthropicClient(
    private val props: AnthropicProperties,
    private val objectMapper: ObjectMapper,
) {
    private val restClient = RestClient.builder()
        .baseUrl("https://api.anthropic.com")
        .defaultHeader("x-api-key", props.apiKey)
        .defaultHeader("anthropic-version", "2023-06-01")
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
            ).apply { setReadTimeout(Duration.ofSeconds(30)) }
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

        val response = restClient.post()
            .uri("/v1/messages")
            .body(body)
            .retrieve()
            .body(MessagesResponse::class.java)!!

        val text = response.content.first().text
            .trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        return objectMapper.readValue<BlocksPayload>(text).blocks.map { b ->
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
    }

    private data class MessagesResponse(val content: List<ContentBlock>)
    private data class ContentBlock(val text: String)

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
        private const val MAX_TOKENS = 4096

        private val SYSTEM_PROMPT = """
            당신은 여행 일정 전문가입니다.
            사용자의 조건에 맞는 여행 일정 블록을 생성하고,
            반드시 아래 JSON 형식만 반환하세요.
            마크다운 코드블록, 설명 텍스트 없이 순수 JSON만 출력하세요.
        """.trimIndent()
    }
}
