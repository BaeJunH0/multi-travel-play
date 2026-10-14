package demo.travel.block

import demo.travel.block.application.BlockService
import demo.travel.block.application.dto.BlockCommand
import demo.travel.block.event.BlockCostChangedEvent
import demo.travel.block.event.BlockDeletedEvent
import demo.travel.common.TripCategory
import demo.travel.common.exception.VersionConflictException
import demo.travel.trip.Trip
import demo.travel.trip.TripMember
import demo.travel.trip.TripMemberRepository
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

class BlockServiceTest : BehaviorSpec({

    val blockRepository = mockk<BlockRepository>()
    val tripMemberRepository = mockk<TripMemberRepository>()
    val userRepository = mockk<UserRepository>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val clock = Clock.fixed(Instant.parse("2026-10-14T03:00:00Z"), ZoneOffset.UTC)
    val now = LocalDateTime.now(clock)
    val service = BlockService(blockRepository, tripMemberRepository, userRepository, eventPublisher, clock)

    val userId = UUID.randomUUID()
    val otherId = UUID.randomUUID()
    val tripId = UUID.randomUUID()
    val blockId = UUID.randomUUID()

    val user = User(id = userId, email = "user@test.com", nickname = "유저", provider = AuthProvider.LOCAL)
    val otherUser = User(id = otherId, email = "other@test.com", nickname = "다른유저", provider = AuthProvider.LOCAL)
    val trip = Trip(
        id = tripId, owner = user, title = "여행", destination = "서울",
        startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 3),
    )

    fun memberWith(role: TripRole, u: User = user) = TripMember(trip = trip, user = u, role = role)

    // 잠금이 있으면 기본으로 아직 만료되지 않은 잠금
    fun makeBlock(
        version: Long = 0L,
        lockedBy: User? = null,
        lockExpiresAt: LocalDateTime? = lockedBy?.let { now.plusMinutes(1) },
        id: UUID = blockId,
    ) = ScheduleBlock(
        id = id, trip = trip, dayNumber = 1, position = 1.0,
        blockType = TripCategory.PLACE, placeName = "경복궁", createdBy = user,
        version = version, lockedBy = lockedBy, lockExpiresAt = lockExpiresAt,
    )

    beforeEach { clearAllMocks() }

    given("getBlocks") {
        `when`("멤버인 경우") {
            then("블록 목록을 반환한다") {
                val blocks = listOf(makeBlock(), makeBlock())
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.VIEWER)
                every { blockRepository.findAllByTripIdOrderByDayNumberAscPositionAsc(tripId) } returns blocks

                val result = service.getBlocks(tripId, userId)
                result.size shouldBe 2
            }
        }

        `when`("멤버가 아닌 경우") {
            then("403 FORBIDDEN을 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns null

                val ex = shouldThrow<ResponseStatusException> { service.getBlocks(tripId, userId) }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }
    }

    given("addBlock") {
        val command = BlockCommand.Create(
            tripId = tripId, dayNumber = 1, blockType = TripCategory.FOOD, placeName = "맛집",
            startTime = "12:00", durationMin = 60, cost = 20000, memo = null, userId = userId,
        )

        `when`("EDITOR 권한일 때") {
            then("블록을 저장하고 BlockResult를 반환한다") {
                val editorMember = memberWith(TripRole.EDITOR)
                val savedBlock = ScheduleBlock(
                    trip = trip, dayNumber = 1, position = 2.0,
                    blockType = TripCategory.FOOD, placeName = "맛집", createdBy = user, cost = 20000,
                )
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns editorMember
                every { blockRepository.findTopByTripIdAndDayNumberOrderByPositionDesc(tripId, 1) } returns makeBlock()
                every { blockRepository.save(any()) } returns savedBlock
                every { userRepository.getReferenceById(userId) } returns user

                val result = service.addBlock(command)
                result.placeName shouldBe "맛집"
                verify { blockRepository.save(match { it.position == 2.0 }) }  // lastPosition(1.0) + 1.0
                verify { eventPublisher.publishEvent(match<Any> { it is BlockCostChangedEvent && it.cost == 20000 }) }
            }
        }

        `when`("첫 번째 블록 추가 (기존 블록 없음)") {
            then("position 1.0으로 저장한다") {
                val editorMember = memberWith(TripRole.EDITOR)
                val savedBlock = ScheduleBlock(
                    trip = trip, dayNumber = 1, position = 1.0,
                    blockType = TripCategory.FOOD, placeName = "맛집", createdBy = user,
                )
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns editorMember
                every { blockRepository.findTopByTripIdAndDayNumberOrderByPositionDesc(tripId, 1) } returns null
                every { blockRepository.save(any()) } returns savedBlock
                every { userRepository.getReferenceById(userId) } returns user

                val result = service.addBlock(command)
                verify { blockRepository.save(match { it.position == 1.0 }) }  // 0.0 + 1.0
            }
        }

        `when`("VIEWER 권한일 때") {
            then("403 FORBIDDEN을 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.VIEWER)

                val ex = shouldThrow<ResponseStatusException> { service.addBlock(command) }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }

        `when`("block에 쓰지 않는 카테고리(FLIGHT)일 때") {
            then("400 BAD_REQUEST를 던지고 저장하지 않는다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)

                val ex = shouldThrow<ResponseStatusException> {
                    service.addBlock(command.copy(blockType = TripCategory.FLIGHT))
                }
                ex.statusCode shouldBe HttpStatus.BAD_REQUEST
                verify(exactly = 0) { blockRepository.save(any()) }
            }
        }
    }

    given("updateBlock") {
        val command = BlockCommand.Update(
            tripId = tripId, blockId = blockId, placeName = "수정된 장소",
            startTime = null, durationMin = null, cost = null, memo = null, version = 0L, userId = userId,
        )

        `when`("잠금 없이 version이 일치할 때") {
            then("블록을 수정하고 반환한다") {
                val block = makeBlock(version = 0L, lockedBy = null)
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block
                every { blockRepository.flush() } just Runs

                val result = service.updateBlock(command)
                result.placeName shouldBe "수정된 장소"
                verify(exactly = 0) { eventPublisher.publishEvent(any()) }  // cost가 command에 없으면 이벤트 미발행
            }
        }

        `when`("cost가 변경될 때") {
            then("BlockCostChangedEvent를 발행한다") {
                val block = makeBlock(version = 0L, lockedBy = null)
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block
                every { blockRepository.flush() } just Runs

                service.updateBlock(command.copy(cost = 15000))

                verify { eventPublisher.publishEvent(match<Any> { it is BlockCostChangedEvent && it.blockId == blockId && it.cost == 15000 }) }
            }
        }

        `when`("version이 불일치할 때") {
            then("VersionConflictException을 던진다") {
                val block = makeBlock(version = 1L)  // 서버 version=1, 요청 version=0
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block

                val ex = shouldThrow<VersionConflictException> { service.updateBlock(command) }
                ex.currentBlock.version shouldBe 1L
            }
        }

        `when`("다른 사용자가 잠근 블록일 때") {
            then("423 LOCKED를 던진다") {
                val block = makeBlock(version = 0L, lockedBy = otherUser)
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block

                val ex = shouldThrow<ResponseStatusException> { service.updateBlock(command) }
                ex.statusCode.value() shouldBe 423
            }
        }

        `when`("다른 사용자의 잠금이 만료됐을 때") {
            then("잠금이 없는 것으로 보고 정상 수정된다") {
                val block = makeBlock(version = 0L, lockedBy = otherUser, lockExpiresAt = now.minusSeconds(1))
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block
                every { blockRepository.flush() } just Runs

                val result = service.updateBlock(command)
                result.placeName shouldBe "수정된 장소"
            }
        }

        `when`("본인이 잠근 블록일 때") {
            then("정상 수정된다") {
                val block = makeBlock(version = 0L, lockedBy = user)
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block
                every { blockRepository.flush() } just Runs

                val result = service.updateBlock(command)
                result.placeName shouldBe "수정된 장소"
            }
        }

        `when`("tripId가 다른 블록일 때") {
            then("404 NOT_FOUND를 던진다") {
                val otherTripId = UUID.randomUUID()
                val block = makeBlock()
                every { tripMemberRepository.findByTripIdAndUserId(otherTripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block  // block.trip.id == tripId (다름)

                val ex = shouldThrow<ResponseStatusException> {
                    service.updateBlock(command.copy(tripId = otherTripId))
                }
                ex.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    }

    given("moveBlock") {
        val command = BlockCommand.Move(tripId = tripId, blockId = blockId, dayNumber = 2, position = 1.5, version = 0L, userId = userId)

        `when`("version이 일치할 때") {
            then("dayNumber와 position을 변경하고 반환한다") {
                val block = makeBlock(version = 0L)
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block
                every { blockRepository.flush() } just Runs

                val result = service.moveBlock(command)
                result.dayNumber shouldBe 2
                result.position shouldBe 1.5
            }
        }

        `when`("version이 불일치할 때") {
            then("VersionConflictException을 던진다") {
                val block = makeBlock(version = 2L)
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block

                shouldThrow<VersionConflictException> { service.moveBlock(command) }
            }
        }

        `when`("다른 사용자가 잠근 블록일 때") {
            then("423 LOCKED를 던지고 위치를 바꾸지 않는다") {
                val block = makeBlock(version = 0L, lockedBy = otherUser)
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block

                val ex = shouldThrow<ResponseStatusException> { service.moveBlock(command) }
                ex.statusCode.value() shouldBe 423
                block.dayNumber shouldBe 1
                block.position shouldBe 1.0
            }
        }

        `when`("본인이 잠근 블록일 때") {
            then("정상 이동한다") {
                val block = makeBlock(version = 0L, lockedBy = user)
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block
                every { blockRepository.flush() } just Runs

                val result = service.moveBlock(command)
                result.dayNumber shouldBe 2
            }
        }
    }

    given("deleteBlock") {
        `when`("EDITOR 권한일 때") {
            then("블록을 삭제한다") {
                val block = makeBlock()
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block
                every { blockRepository.delete(block) } just Runs

                service.deleteBlock(tripId, blockId, userId)

                verify { blockRepository.delete(block) }
                verify { eventPublisher.publishEvent(match<Any> { it is BlockDeletedEvent && it.blockId == blockId }) }
            }
        }

        `when`("다른 사용자가 잠근 블록일 때") {
            then("423 LOCKED를 던지고 삭제하지 않는다") {
                val block = makeBlock(lockedBy = otherUser)
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block

                val ex = shouldThrow<ResponseStatusException> { service.deleteBlock(tripId, blockId, userId) }
                ex.statusCode.value() shouldBe 423
                verify(exactly = 0) { blockRepository.delete(any()) }
                verify(exactly = 0) { eventPublisher.publishEvent(any()) }
            }
        }

        `when`("본인이 잠근 블록일 때") {
            then("블록을 삭제한다") {
                val block = makeBlock(lockedBy = user)
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block
                every { blockRepository.delete(block) } just Runs

                service.deleteBlock(tripId, blockId, userId)

                verify { blockRepository.delete(block) }
            }
        }

        `when`("VIEWER 권한일 때") {
            then("403 FORBIDDEN을 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.VIEWER)

                val ex = shouldThrow<ResponseStatusException> { service.deleteBlock(tripId, blockId, userId) }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }
    }

    given("lockBlock") {
        `when`("잠금이 없는 블록일 때") {
            then("자신으로 lockedBy를 설정하고 만료 시각을 TTL(3분) 뒤로 둔다") {
                val block = makeBlock(lockedBy = null)
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block
                every { userRepository.getReferenceById(userId) } returns user

                val result = service.lockBlock(tripId, blockId, userId)
                result.lockedBy shouldBe userId
                block.lockExpiresAt shouldBe now.plusMinutes(3)
            }
        }

        `when`("다른 사용자의 잠금이 만료됐을 때") {
            then("잠금을 가져온다") {
                val block = makeBlock(lockedBy = otherUser, lockExpiresAt = now)
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block
                every { userRepository.getReferenceById(userId) } returns user

                val result = service.lockBlock(tripId, blockId, userId)
                result.lockedBy shouldBe userId
                block.lockExpiresAt shouldBe now.plusMinutes(3)
            }
        }

        `when`("자신이 이미 잠근 블록일 때") {
            then("lockedBy를 자신으로 유지하며 정상 반환한다") {
                val block = makeBlock(lockedBy = user)
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block
                every { userRepository.getReferenceById(userId) } returns user

                val result = service.lockBlock(tripId, blockId, userId)
                result.lockedBy shouldBe userId
            }
        }

        `when`("다른 사용자가 이미 잠근 블록일 때") {
            then("423 LOCKED를 던진다") {
                val block = makeBlock(lockedBy = otherUser)
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block

                val ex = shouldThrow<ResponseStatusException> { service.lockBlock(tripId, blockId, userId) }
                ex.statusCode.value() shouldBe 423
            }
        }
    }

    given("unlockBlock") {
        `when`("본인이 잠근 블록일 때") {
            then("lockedBy와 만료 시각을 null로 해제한다") {
                val block = makeBlock(lockedBy = user)
                every { blockRepository.findByIdOrNull(blockId) } returns block

                service.unlockBlock(tripId, blockId, userId)

                block.lockedBy shouldBe null
                block.lockExpiresAt shouldBe null
            }
        }

        `when`("다른 사용자가 잠근 블록일 때") {
            then("잠금을 건드리지 않는다") {
                val block = makeBlock(lockedBy = otherUser)
                every { blockRepository.findByIdOrNull(blockId) } returns block

                service.unlockBlock(tripId, blockId, userId)

                block.lockedBy shouldBe otherUser
            }
        }
    }

    given("releaseLocks") {
        `when`("유저가 여행에서 잠금을 잡고 있을 때") {
            then("블록마다 잠금 컬럼만 해제하고, 실제로 해제된 blockId만 반환한다") {
                val block1 = makeBlock(lockedBy = user, id = UUID.randomUUID())
                val block2 = makeBlock(lockedBy = user, id = UUID.randomUUID())
                every { blockRepository.findAllByTripIdAndLockedById(tripId, userId) } returns listOf(block1, block2)
                every { blockRepository.releaseLock(block1.id, userId) } returns 1
                every { blockRepository.releaseLock(block2.id, userId) } returns 0  // 그 사이 잠금이 바뀐 경우

                service.releaseLocks(tripId, userId) shouldBe listOf(block1.id)
            }
        }
    }

    given("refreshLocks") {
        `when`("잠금자의 SSE 연결이 살아 있을 때") {
            then("만료가 지났더라도 만료 시각을 지금부터 TTL 뒤로 연장한다") {
                val block = makeBlock(lockedBy = user, lockExpiresAt = now.minusSeconds(10))
                every { blockRepository.findAllByLockedByIsNotNull() } returns listOf(block)
                every { blockRepository.extendLock(blockId, userId, any()) } returns 1

                service.refreshLocks(setOf(tripId to userId)) shouldBe emptyList()
                verify(exactly = 1) { blockRepository.extendLock(blockId, userId, now.plusMinutes(3)) }
                verify(exactly = 0) { blockRepository.releaseExpiredLock(any(), any()) }
            }
        }

        `when`("잠금자의 연결이 없고 만료가 지났을 때") {
            then("잠금을 해제하고 (tripId, blockId)를 반환한다") {
                val block = makeBlock(lockedBy = user, lockExpiresAt = now.minusSeconds(1))
                every { blockRepository.findAllByLockedByIsNotNull() } returns listOf(block)
                every { blockRepository.releaseExpiredLock(blockId, now) } returns 1

                service.refreshLocks(setOf(tripId to otherId)) shouldBe listOf(tripId to blockId)
            }
        }

        `when`("만료된 잠금을 읽은 뒤 다른 사용자가 새로 잠갔을 때 (UPDATE 조건에 걸려 0행)") {
            then("해제 목록에 넣지 않는다") {
                val block = makeBlock(lockedBy = user, lockExpiresAt = now.minusSeconds(1))
                every { blockRepository.findAllByLockedByIsNotNull() } returns listOf(block)
                every { blockRepository.releaseExpiredLock(blockId, now) } returns 0

                service.refreshLocks(emptySet()) shouldBe emptyList()
            }
        }

        `when`("잠금자의 연결이 없지만 아직 만료 전일 때") {
            then("아무것도 바꾸지 않는다") {
                val block = makeBlock(lockedBy = user, lockExpiresAt = now.plusMinutes(2))
                every { blockRepository.findAllByLockedByIsNotNull() } returns listOf(block)

                service.refreshLocks(emptySet()) shouldBe emptyList()
                verify(exactly = 0) { blockRepository.extendLock(any(), any(), any()) }
                verify(exactly = 0) { blockRepository.releaseExpiredLock(any(), any()) }
            }
        }
    }

    given("reorderBlocks") {
        `when`("Day 1에 3개, Day 2에 2개의 블록이 있을 때") {
            then("각 Day 내 position을 1.0, 2.0, 3.0, ...으로 재정규화한다") {
                val day1Blocks = listOf(
                    ScheduleBlock(trip = trip, dayNumber = 1, position = 0.1, blockType = TripCategory.PLACE, placeName = "A", createdBy = user),
                    ScheduleBlock(trip = trip, dayNumber = 1, position = 0.15, blockType = TripCategory.FOOD, placeName = "B", createdBy = user),
                    ScheduleBlock(trip = trip, dayNumber = 1, position = 0.2, blockType = TripCategory.CAFE, placeName = "C", createdBy = user),
                )
                val day2Blocks = listOf(
                    ScheduleBlock(trip = trip, dayNumber = 2, position = 0.5, blockType = TripCategory.HOTEL, placeName = "D", createdBy = user),
                    ScheduleBlock(trip = trip, dayNumber = 2, position = 0.6, blockType = TripCategory.TRANSPORT, placeName = "E", createdBy = user),
                )
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findAllByTripIdOrderByDayNumberAscPositionAsc(tripId) } returns day1Blocks + day2Blocks

                service.reorderBlocks(tripId, userId)

                day1Blocks[0].position shouldBe 1.0
                day1Blocks[1].position shouldBe 2.0
                day1Blocks[2].position shouldBe 3.0
                day2Blocks[0].position shouldBe 1.0
                day2Blocks[1].position shouldBe 2.0
            }
        }
    }
})
