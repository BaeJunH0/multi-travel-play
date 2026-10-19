package demo.travel.ai

import demo.travel.ai.application.AiService
import demo.travel.ai.client.AnthropicClient
import demo.travel.ai.client.GooglePlacesClient
import demo.travel.ai.application.dto.AiCommand
import demo.travel.block.BlockRepository
import demo.travel.block.ScheduleBlock
import demo.travel.common.TripCategory
import demo.travel.trip.Trip
import demo.travel.trip.TripMember
import demo.travel.trip.TripMemberRepository
import demo.travel.trip.TripRepository
import demo.travel.trip.TripRole
import demo.travel.user.AuthProvider
import demo.travel.user.User
import demo.travel.user.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.*

class AiServiceTest : BehaviorSpec({

    val anthropicClient = mockk<AnthropicClient>()
    val placesClient = mockk<GooglePlacesClient>()
    val tripRepository = mockk<TripRepository>()
    val tripMemberRepository = mockk<TripMemberRepository>()
    val blockRepository = mockk<BlockRepository>()
    val userRepository = mockk<UserRepository>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val service = AiService(anthropicClient, placesClient, tripRepository, tripMemberRepository, blockRepository, userRepository, eventPublisher)

    val tripId = UUID.randomUUID()
    val user = User(email = "user@test.com", nickname = "유저", provider = AuthProvider.LOCAL)
    val trip = Trip(
        id = tripId, owner = user, title = "여행", destination = "서울",
        startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 3),
    )

    fun selected(tempId: String, blockType: TripCategory) = AiCommand.SelectedBlock(
        tempId = tempId, blockType = blockType, placeName = "장소-$tempId", startTime = "10:00",
        durationMin = 60, cost = null, memo = null, dayNumber = 1, position = 1.0,
    )

    beforeEach {
        clearAllMocks()
        every { tripMemberRepository.findByTripIdAndUserId(tripId, user.id) } returns
            TripMember(trip = trip, user = user, role = TripRole.EDITOR)
        every { tripRepository.findByIdOrNull(tripId) } returns trip
        every { placesClient.findLatLng(any()) } returns (37.5 to 127.0)
        every { blockRepository.save(any()) } answers { firstArg<ScheduleBlock>() }
        every { userRepository.getReferenceById(user.id) } returns user
    }

    given("apply") {
        `when`("block용 카테고리만 선택했을 때") {
            then("모두 저장하고 tempId를 그대로 돌려준다") {
                val command = AiCommand.Apply(tripId, listOf(selected("temp-1", TripCategory.FOOD), selected("temp-2", TripCategory.PLACE)), user.id)

                val result = service.apply(command)

                result.addedBlocks.map { it.tempId } shouldBe listOf("temp-1", "temp-2")
                result.addedBlocks.first().block.lat shouldBe 37.5
                verify(exactly = 2) { blockRepository.save(any()) }
            }
        }

        `when`("block에 쓰지 않는 카테고리(ETC)가 섞여 있을 때") {
            then("400 BAD_REQUEST를 던지고 아무것도 저장하지 않는다") {
                val command = AiCommand.Apply(tripId, listOf(selected("temp-1", TripCategory.FOOD), selected("temp-2", TripCategory.ETC)), user.id)

                val ex = shouldThrow<ResponseStatusException> { service.apply(command) }
                ex.statusCode shouldBe HttpStatus.BAD_REQUEST
                verify(exactly = 0) { blockRepository.save(any()) }
                verify(exactly = 0) { placesClient.findLatLng(any()) }
            }
        }

        `when`("VIEWER 권한일 때") {
            then("403 FORBIDDEN을 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, user.id) } returns
                    TripMember(trip = trip, user = user, role = TripRole.VIEWER)
                val command = AiCommand.Apply(tripId, listOf(selected("temp-1", TripCategory.FOOD)), user.id)

                val ex = shouldThrow<ResponseStatusException> { service.apply(command) }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }
    }
})
