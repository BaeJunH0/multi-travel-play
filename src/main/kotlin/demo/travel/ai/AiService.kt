package demo.travel.ai

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import demo.travel.ai.dto.AiRequest
import demo.travel.ai.dto.AiBlock
import demo.travel.ai.dto.AppliedBlock
import demo.travel.ai.dto.ApplyResponse
import demo.travel.ai.dto.GenerateResponse
import demo.travel.block.dto.BlockResponse
import demo.travel.block.ScheduleBlock
import demo.travel.block.BlockRepository
import demo.travel.trip.TripMemberRepository
import demo.travel.trip.TripRepository
import demo.travel.trip.TripRole
import demo.travel.user.User
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class AiService(
    private val openAiClient: OpenAiClient,
    private val placesClient: GooglePlacesClient,
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val blockRepository: BlockRepository,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun generate(tripId: UUID, request: AiRequest.GenerateRequest, userId: UUID): GenerateResponse {
        requireEditorOrAbove(tripId, userId)
        val trip = tripRepository.findByIdOrNull(tripId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

        val totalDays = ChronoUnit.DAYS.between(trip.startDate, trip.endDate).toInt() + 1
        val prompt = buildPrompt(trip.destination, trip.startDate.toString(), trip.endDate.toString(), totalDays, request)

        val blocks = openAiClient.generate(prompt)
        val generationId = UUID.randomUUID().toString()

        redisTemplate.opsForValue().set(
            redisKey(generationId),
            objectMapper.writeValueAsString(blocks),
            10, TimeUnit.MINUTES,
        )

        return GenerateResponse(generationId, blocks)
    }

    @Transactional
    fun apply(tripId: UUID, request: AiRequest.ApplyRequest, user: User): ApplyResponse {
        requireEditorOrAbove(tripId, user.id)

        val json = redisTemplate.opsForValue().get(redisKey(request.generationId))
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "생성 결과가 만료되었습니다.")

        val trip = tripRepository.findByIdOrNull(tripId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val allBlocks: List<AiBlock> = objectMapper.readValue(json)
        val blockMap = allBlocks.associateBy { it.tempId }

        val added = request.selectedBlocks.map { selected ->
            val aiBlock = blockMap[selected.tempId]
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "tempId ${selected.tempId} 없음")

            val latLng = placesClient.findLatLng(aiBlock.placeName)
            val saved = blockRepository.save(
                ScheduleBlock(
                    trip = trip,
                    dayNumber = selected.dayNumber,
                    position = selected.position,
                    blockType = aiBlock.blockType,
                    placeName = aiBlock.placeName,
                    lat = latLng?.first,
                    lng = latLng?.second,
                    startTime = aiBlock.startTime?.let { LocalTime.parse(it) },
                    durationMin = aiBlock.durationMin,
                    cost = aiBlock.cost,
                    memo = aiBlock.memo,
                    createdBy = user,
                )
            )
            AppliedBlock(tempId = selected.tempId, block = BlockResponse.of(saved))
        }

        redisTemplate.delete(redisKey(request.generationId))
        return ApplyResponse(added)
    }

    private fun requireEditorOrAbove(tripId: UUID, userId: UUID) {
        val member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
        if (member.role == TripRole.VIEWER) throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    private fun redisKey(generationId: String) = "ai:generation:$generationId"

    private fun buildPrompt(
        destination: String,
        startDate: String,
        endDate: String,
        totalDays: Int,
        req: AiRequest.GenerateRequest,
    ) = """
        목적지: $destination
        기간: $startDate ~ $endDate (${totalDays}일)
        생성 항목: ${req.tags.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "전체"}
        적용 Day: ${req.targetDays.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "전체"}
        여행 스타일: ${req.style}
        하루 강도: ${req.intensity}
        추가 요청: ${req.extraRequest ?: "없음"}

        반환 형식:
        {
          "blocks": [
            {
              "tempId": "temp-{n}",
              "blockType": "FOOD | CAFE | PLACE | HOTEL | TRANSPORT",
              "placeName": "장소명",
              "startTime": "HH:mm",
              "durationMin": 60,
              "cost": 0,
              "suggestedDay": 1,
              "memo": "메모 또는 null"
            }
          ]
        }
    """.trimIndent()
}
