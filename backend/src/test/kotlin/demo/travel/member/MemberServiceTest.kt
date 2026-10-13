package demo.travel.member

import demo.travel.member.application.MemberService
import demo.travel.member.application.dto.MemberCommand
import demo.travel.trip.Trip
import demo.travel.trip.TripMember
import demo.travel.trip.TripMemberRepository
import demo.travel.trip.TripRole
import demo.travel.user.AuthProvider
import demo.travel.user.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.*

class MemberServiceTest : BehaviorSpec({

    val tripMemberRepository = mockk<TripMemberRepository>()
    val service = MemberService(tripMemberRepository)

    val ownerId = UUID.randomUUID()
    val editorId = UUID.randomUUID()
    val viewerId = UUID.randomUUID()
    val tripId = UUID.randomUUID()

    val owner = User(id = ownerId, email = "owner@test.com", nickname = "오너", provider = AuthProvider.LOCAL)
    val editor = User(id = editorId, email = "editor@test.com", nickname = "에디터", provider = AuthProvider.LOCAL)
    val viewer = User(id = viewerId, email = "viewer@test.com", nickname = "뷰어", provider = AuthProvider.LOCAL)
    val trip = Trip(
        id = tripId, owner = owner, title = "여행", destination = "서울",
        startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 3),
    )

    val ownerMember = TripMember(trip = trip, user = owner, role = TripRole.OWNER)
    val editorMember = TripMember(trip = trip, user = editor, role = TripRole.EDITOR)
    val viewerMember = TripMember(trip = trip, user = viewer, role = TripRole.VIEWER)

    beforeEach { clearAllMocks() }

    given("getMembers") {
        `when`("멤버인 경우") {
            then("전체 멤버 목록을 반환한다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, viewerId) } returns viewerMember
                every { tripMemberRepository.findAllByTripId(tripId) } returns listOf(ownerMember, editorMember, viewerMember)

                val result = service.getMembers(tripId, viewerId)
                result shouldHaveSize 3
                result.map { it.role } shouldBe listOf(TripRole.OWNER, TripRole.EDITOR, TripRole.VIEWER)
            }
        }

        `when`("멤버가 아닌 경우") {
            then("403 FORBIDDEN을 던진다") {
                val outsiderId = UUID.randomUUID()
                every { tripMemberRepository.findByTripIdAndUserId(tripId, outsiderId) } returns null

                val ex = shouldThrow<ResponseStatusException> { service.getMembers(tripId, outsiderId) }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }
    }

    given("updateRole") {
        `when`("OWNER가 EDITOR를 VIEWER로 변경할 때") {
            then("역할이 변경된다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, ownerId) } returns ownerMember
                every { tripMemberRepository.findByTripIdAndUserId(tripId, editorId) } returns editorMember

                service.updateRole(MemberCommand.UpdateRole(tripId, editorId, TripRole.VIEWER, ownerId))

                editorMember.role shouldBe TripRole.VIEWER
            }
        }

        `when`("OWNER가 VIEWER를 EDITOR로 승격할 때") {
            then("역할이 변경된다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, ownerId) } returns ownerMember
                every { tripMemberRepository.findByTripIdAndUserId(tripId, viewerId) } returns viewerMember

                service.updateRole(MemberCommand.UpdateRole(tripId, viewerId, TripRole.EDITOR, ownerId))

                viewerMember.role shouldBe TripRole.EDITOR
            }
        }

        `when`("요청자가 EDITOR일 때") {
            then("403 FORBIDDEN을 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, editorId) } returns editorMember

                val ex = shouldThrow<ResponseStatusException> {
                    service.updateRole(MemberCommand.UpdateRole(tripId, viewerId, TripRole.EDITOR, editorId))
                }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }

        `when`("대상이 OWNER일 때") {
            then("403 FORBIDDEN을 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, ownerId) } returns ownerMember

                val ex = shouldThrow<ResponseStatusException> {
                    service.updateRole(MemberCommand.UpdateRole(tripId, ownerId, TripRole.EDITOR, ownerId))
                }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }

        `when`("newRole이 OWNER일 때") {
            then("400 BAD_REQUEST를 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, ownerId) } returns ownerMember

                val ex = shouldThrow<ResponseStatusException> {
                    service.updateRole(MemberCommand.UpdateRole(tripId, editorId, TripRole.OWNER, ownerId))
                }
                ex.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }

        `when`("요청자가 해당 여행의 멤버가 아닐 때") {
            then("403 FORBIDDEN을 던진다") {
                val outsiderId = UUID.randomUUID()
                every { tripMemberRepository.findByTripIdAndUserId(tripId, outsiderId) } returns null

                val ex = shouldThrow<ResponseStatusException> {
                    service.updateRole(MemberCommand.UpdateRole(tripId, editorId, TripRole.VIEWER, outsiderId))
                }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }
    }

    given("removeMember") {
        `when`("OWNER가 EDITOR를 제거할 때") {
            then("멤버를 삭제한다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, ownerId) } returns ownerMember
                every { tripMemberRepository.findByTripIdAndUserId(tripId, editorId) } returns editorMember
                every { tripMemberRepository.delete(editorMember) } just Runs

                service.removeMember(tripId, editorId, ownerId)

                verify { tripMemberRepository.delete(editorMember) }
            }
        }

        `when`("OWNER가 VIEWER를 제거할 때") {
            then("멤버를 삭제한다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, ownerId) } returns ownerMember
                every { tripMemberRepository.findByTripIdAndUserId(tripId, viewerId) } returns viewerMember
                every { tripMemberRepository.delete(viewerMember) } just Runs

                service.removeMember(tripId, viewerId, ownerId)

                verify { tripMemberRepository.delete(viewerMember) }
            }
        }

        `when`("OWNER를 제거하려 할 때") {
            then("403 FORBIDDEN을 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, ownerId) } returns ownerMember

                val ex = shouldThrow<ResponseStatusException> {
                    service.removeMember(tripId, ownerId, ownerId)
                }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
                verify(exactly = 0) { tripMemberRepository.delete(any()) }
            }
        }

        `when`("요청자가 EDITOR일 때") {
            then("403 FORBIDDEN을 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, editorId) } returns editorMember

                val ex = shouldThrow<ResponseStatusException> {
                    service.removeMember(tripId, viewerId, editorId)
                }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }

        `when`("제거 대상이 존재하지 않을 때") {
            then("404 NOT_FOUND를 던진다") {
                val unknownId = UUID.randomUUID()
                every { tripMemberRepository.findByTripIdAndUserId(tripId, ownerId) } returns ownerMember
                every { tripMemberRepository.findByTripIdAndUserId(tripId, unknownId) } returns null

                val ex = shouldThrow<ResponseStatusException> {
                    service.removeMember(tripId, unknownId, ownerId)
                }
                ex.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    }
})
