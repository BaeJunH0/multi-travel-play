package demo.travel.block

import demo.travel.block.application.BlockService
import demo.travel.block.application.dto.BlockCommand
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
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.*

class BlockServiceTest : BehaviorSpec({

    val blockRepository = mockk<BlockRepository>()
    val tripMemberRepository = mockk<TripMemberRepository>()
    val userRepository = mockk<UserRepository>()
    val service = BlockService(blockRepository, tripMemberRepository, userRepository)

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

    fun makeBlock(version: Long = 0L, lockedBy: User? = null) = ScheduleBlock(
        id = blockId, trip = trip, dayNumber = 1, position = 1.0,
        blockType = BlockType.PLACE, placeName = "경복궁", createdBy = user,
        version = version, lockedBy = lockedBy,
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
            tripId = tripId, dayNumber = 1, blockType = BlockType.FOOD, placeName = "맛집",
            startTime = "12:00", durationMin = 60, cost = 20000, memo = null, userId = userId,
        )

        `when`("EDITOR 권한일 때") {
            then("블록을 저장하고 BlockResult를 반환한다") {
                val editorMember = memberWith(TripRole.EDITOR)
                val savedBlock = ScheduleBlock(
                    trip = trip, dayNumber = 1, position = 2.0,
                    blockType = BlockType.FOOD, placeName = "맛집", createdBy = user,
                )
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns editorMember
                every { blockRepository.findTopByTripIdAndDayNumberOrderByPositionDesc(tripId, 1) } returns makeBlock()
                every { blockRepository.save(any()) } returns savedBlock
                every { userRepository.getReferenceById(userId) } returns user

                val result = service.addBlock(command)
                result.placeName shouldBe "맛집"
                verify { blockRepository.save(match { it.position == 2.0 }) }  // lastPosition(1.0) + 1.0
            }
        }

        `when`("첫 번째 블록 추가 (기존 블록 없음)") {
            then("position 1.0으로 저장한다") {
                val editorMember = memberWith(TripRole.EDITOR)
                val savedBlock = ScheduleBlock(
                    trip = trip, dayNumber = 1, position = 1.0,
                    blockType = BlockType.FOOD, placeName = "맛집", createdBy = user,
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
            then("자신으로 lockedBy를 설정한다") {
                val block = makeBlock(lockedBy = null)
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { blockRepository.findByIdOrNull(blockId) } returns block
                every { userRepository.getReferenceById(userId) } returns user

                val result = service.lockBlock(tripId, blockId, userId)
                result.lockedBy shouldBe userId
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
            then("lockedBy를 null로 해제한다") {
                val block = makeBlock(lockedBy = user)
                every { blockRepository.findByIdOrNull(blockId) } returns block

                service.unlockBlock(tripId, blockId, userId)

                block.lockedBy shouldBe null
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

    given("reorderBlocks") {
        `when`("Day 1에 3개, Day 2에 2개의 블록이 있을 때") {
            then("각 Day 내 position을 1.0, 2.0, 3.0, ...으로 재정규화한다") {
                val day1Blocks = listOf(
                    ScheduleBlock(trip = trip, dayNumber = 1, position = 0.1, blockType = BlockType.PLACE, placeName = "A", createdBy = user),
                    ScheduleBlock(trip = trip, dayNumber = 1, position = 0.15, blockType = BlockType.FOOD, placeName = "B", createdBy = user),
                    ScheduleBlock(trip = trip, dayNumber = 1, position = 0.2, blockType = BlockType.CAFE, placeName = "C", createdBy = user),
                )
                val day2Blocks = listOf(
                    ScheduleBlock(trip = trip, dayNumber = 2, position = 0.5, blockType = BlockType.HOTEL, placeName = "D", createdBy = user),
                    ScheduleBlock(trip = trip, dayNumber = 2, position = 0.6, blockType = BlockType.TRANSPORT, placeName = "E", createdBy = user),
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
