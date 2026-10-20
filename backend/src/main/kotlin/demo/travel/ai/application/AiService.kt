package demo.travel.ai.application

import demo.travel.ai.application.dto.AiCommand
import demo.travel.ai.application.dto.AiResult
import demo.travel.ai.client.AnthropicClient
import demo.travel.ai.client.GooglePlacesClient
import demo.travel.block.BlockRepository
import demo.travel.block.ScheduleBlock
import demo.travel.block.application.dto.BlockResult
import demo.travel.block.event.toCostChangedEventOrNull
import demo.travel.trip.TripMemberRepository
import demo.travel.trip.TripRepository
import demo.travel.trip.TripRole
import demo.travel.user.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID

// generate는 외부 AI 호출이 길어(최대 120초) DB 트랜잭션을 잡지 않는다. 그래서 클래스 단위 @Transactional을 두지 않는다
@Service
class AiService(
    private val anthropicClient: AnthropicClient,
    private val placesClient: GooglePlacesClient,
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val blockRepository: BlockRepository,
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun generate(command: AiCommand.Generate): AiResult.Generate {
        requireEditorOrAbove(command.tripId, command.userId)
        val trip = tripRepository.findByIdOrNull(command.tripId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

        val totalDays = ChronoUnit.DAYS.between(trip.startDate, trip.endDate).toInt() + 1
        val prompt = buildPrompt(trip.destination, trip.startDate.toString(), trip.endDate.toString(), totalDays, command)

        val blocks = anthropicClient.generate(prompt)
        return AiResult.Generate(blocks.map { AiResult.SuggestedBlock.of(it) })
    }

    @Transactional
    fun apply(command: AiCommand.Apply): AiResult.Apply {
        requireEditorOrAbove(command.tripId, command.userId)
        command.selectedBlocks.firstOrNull { !it.blockType.isBlockType }?.let {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "블록에 사용할 수 없는 카테고리입니다: ${it.blockType}")
        }

        val trip = tripRepository.findByIdOrNull(command.tripId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val creator = userRepository.getReferenceById(command.userId)

        val added = command.selectedBlocks.map { selected ->
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
                    createdBy = creator,
                ),
            )
            saved.toCostChangedEventOrNull()?.let { eventPublisher.publishEvent(it) }
            AiResult.AppliedBlock(tempId = selected.tempId, block = BlockResult.of(saved))
        }

        return AiResult.Apply(added)
    }

    private fun requireEditorOrAbove(tripId: UUID, userId: UUID) {
        val member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
        if (member.role == TripRole.VIEWER) throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    private fun buildPrompt(destination: String, startDate: String, endDate: String, totalDays: Int, command: AiCommand.Generate) = """
        목적지: $destination
        기간: $startDate ~ $endDate (${totalDays}일)
        생성 항목: ${command.tags.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "전체"}
        적용 Day: ${command.targetDays.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "전체"}
        여행 스타일: ${command.style}
        하루 강도: ${command.intensity}
        추가 요청: ${command.extraRequest ?: "없음"}

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
