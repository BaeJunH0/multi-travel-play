package demo.travel.block

import demo.travel.block.application.BlockService
import demo.travel.block.application.VersionConflictException
import demo.travel.block.application.dto.BlockResult
import demo.travel.block.presentation.BlockController
import demo.travel.block.presentation.dto.BlockRequest
import demo.travel.common.TripCategory
import demo.travel.sse.application.TripEventPublisher
import demo.travel.sse.application.dto.ConflictPayload
import demo.travel.sse.application.dto.TripEvent
import demo.travel.user.AuthProvider
import demo.travel.user.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.*

class BlockControllerTest :
    BehaviorSpec({

        val blockService = mockk<BlockService>()
        val eventPublisher = mockk<TripEventPublisher>(relaxed = true)
        val controller = BlockController(blockService, eventPublisher)

        val tripId = UUID.randomUUID()
        val blockId = UUID.randomUUID()
        val user = User(email = "user@test.com", nickname = "유저", provider = AuthProvider.LOCAL)

        fun result(version: Long) = BlockResult(
            id = blockId, dayNumber = 1, position = 1.0, blockType = TripCategory.PLACE, placeName = "경복궁",
            lat = null, lng = null, startTime = null, durationMin = null, cost = null, memo = null,
            lockedBy = null, lockedByNickname = null, version = version,
        )

        fun isConflictOf(event: TripEvent, version: Long) = event.action == TripEvent.Action.CONFLICT &&
            (event.payload as ConflictPayload).let { it.blockId == blockId && it.currentBlock.version == version }

        beforeEach { clearAllMocks() }

        given("updateBlock") {
            val request = BlockRequest.Update(
                placeName = "수정",
                startTime = null,
                durationMin = null,
                cost = null,
                memo = null,
                version = 0L,
            )

            `when`("version이 충돌할 때") {
                then("CONFLICT를 브로드캐스트하고 예외를 그대로 던진다") {
                    every { blockService.updateBlock(any()) } throws VersionConflictException(result(version = 3L))

                    shouldThrow<VersionConflictException> { controller.updateBlock(user, tripId, blockId, request) }

                    verify(exactly = 1) { eventPublisher.publish(tripId, match { isConflictOf(it, 3L) }) }
                    verify(exactly = 0) { eventPublisher.publish(tripId, match { it.action == TripEvent.Action.UPDATE }) }
                }
            }

            `when`("정상 수정일 때") {
                then("UPDATE만 브로드캐스트한다") {
                    every { blockService.updateBlock(any()) } returns result(version = 1L)

                    controller.updateBlock(user, tripId, blockId, request)

                    verify(exactly = 1) { eventPublisher.publish(tripId, match { it.action == TripEvent.Action.UPDATE }) }
                    verify(exactly = 0) { eventPublisher.publish(tripId, match { it.action == TripEvent.Action.CONFLICT }) }
                }
            }
        }

        given("moveBlock") {
            val request = BlockRequest.Move(dayNumber = 2, position = 1.5, version = 0L)

            `when`("version이 충돌할 때") {
                then("CONFLICT를 브로드캐스트하고 예외를 그대로 던진다") {
                    every { blockService.moveBlock(any()) } throws VersionConflictException(result(version = 5L))

                    shouldThrow<VersionConflictException> { controller.moveBlock(user, tripId, blockId, request) }

                    verify(exactly = 1) { eventPublisher.publish(tripId, match { isConflictOf(it, 5L) }) }
                    verify(exactly = 0) { eventPublisher.publish(tripId, match { it.action == TripEvent.Action.MOVE }) }
                }
            }
        }
    })
