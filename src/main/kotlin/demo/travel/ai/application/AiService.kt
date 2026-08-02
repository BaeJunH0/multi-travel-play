package demo.travel.ai.application

import demo.travel.ai.client.GooglePlacesClient
import demo.travel.ai.client.OpenAiClient
import demo.travel.ai.presentation.dto.AiRequest
import demo.travel.ai.presentation.dto.AppliedBlock
import demo.travel.ai.presentation.dto.ApplyResponse
import demo.travel.ai.presentation.dto.GenerateResponse
import demo.travel.block.BlockRepository
import demo.travel.block.ScheduleBlock
import demo.travel.block.application.dto.BlockResult
import demo.travel.block.presentation.dto.BlockResponse
import demo.travel.trip.TripMemberRepository
import demo.travel.trip.TripRepository
import demo.travel.trip.TripRole
import demo.travel.user.User
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class AiService(
    private val openAiClient: OpenAiClient,
    private val placesClient: GooglePlacesClient,
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val blockRepository: BlockRepository,
) {
    fun generate(tripId: UUID, request: AiRequest.GenerateRequest, userId: UUID): GenerateResponse {
        requireEditorOrAbove(tripId, userId)
        val trip = tripRepository.findByIdOrNull(tripId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

        val totalDays = ChronoUnit.DAYS.between(trip.startDate, trip.endDate).toInt() + 1
        val prompt = buildPrompt(trip.destination, trip.startDate.toString(), trip.endDate.toString(), totalDays, request)

        val blocks = openAiClient.generate(prompt)
        return GenerateResponse(blocks)
    }

    @Transactional
    fun apply(tripId: UUID, request: AiRequest.ApplyRequest, user: User): ApplyResponse {
        requireEditorOrAbove(tripId, user.id)

        val trip = tripRepository.findByIdOrNull(tripId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

        val added = request.selectedBlocks.map { selected ->
            val latLng = placesClient.findLatLng(selected.placeName)
            val saved = blockRepository.save(
                ScheduleBlock(
                    trip = trip,
                    dayNumber = selected.dayNumber,
                    position = selected.position,
                    blockType = selected.blockType,
                    placeName = selected.placeName,
                    lat = latLng?.first,
                    lng = latLng?.second,
                    startTime = selected.startTime?.let { LocalTime.parse(it) },
                    durationMin = selected.durationMin,
                    cost = selected.cost,
                    memo = selected.memo,
                    createdBy = user,
                )
            )
            AppliedBlock(tempId = selected.tempId, block = BlockResponse.of(BlockResult.of(saved)))
        }

        return ApplyResponse(added)
    }

    private fun requireEditorOrAbove(tripId: UUID, userId: UUID) {
        val member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
        if (member.role == TripRole.VIEWER) throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

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