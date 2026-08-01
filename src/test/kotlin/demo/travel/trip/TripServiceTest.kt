package demo.travel.trip

import demo.travel.trip.dto.TripRequest
import demo.travel.user.AuthProvider
import demo.travel.user.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.*

class TripServiceTest : BehaviorSpec({

    val tripRepository = mockk<TripRepository>()
    val tripMemberRepository = mockk<TripMemberRepository>()
    val service = TripService(tripRepository, tripMemberRepository)

    val userId = UUID.randomUUID()
    val tripId = UUID.randomUUID()
    val user = User(id = userId, email = "user@test.com", nickname = "유저", provider = AuthProvider.LOCAL)
    val trip = Trip(
        id = tripId, owner = user,
        title = "제주도 여행", destination = "제주도",
        startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 3),
    )

    fun memberWith(role: TripRole) = TripMember(trip = trip, user = user, role = role)

    beforeEach { clearAllMocks() }

    given("create") {
        val request = TripRequest.Create("제주도 여행", "제주도", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3))

        `when`("정상 요청일 때") {
            then("여행을 저장하고 OWNER TripMember를 등록한다") {
                every { tripRepository.save(any()) } returns trip
                every { tripMemberRepository.save(any()) } returns memberWith(TripRole.OWNER)
                every { tripMemberRepository.countByTripId(tripId) } returns 1

                val result = service.create(request, user)

                result.id shouldBe tripId
                result.myRole shouldBe TripRole.OWNER
                result.memberCount shouldBe 1
                verify { tripMemberRepository.save(match { it.role == TripRole.OWNER && it.user.id == userId }) }
            }
        }
    }

    given("getList") {
        `when`("사용자가 속한 여행이 2개일 때") {
            then("2개의 TripSummaryResponse를 반환한다") {
                val trip2 = Trip(
                    owner = user, title = "도쿄 여행", destination = "도쿄",
                    startDate = LocalDate.of(2026, 9, 1), endDate = LocalDate.of(2026, 9, 5),
                )
                val members = listOf(memberWith(TripRole.OWNER), TripMember(trip = trip2, user = user, role = TripRole.EDITOR))
                every { tripMemberRepository.findAllWithTripByUserId(userId) } returns members
                every { tripMemberRepository.countByTripId(any()) } returns 1

                val result = service.getList(userId)
                result shouldHaveSize 2
            }
        }

        `when`("속한 여행이 없을 때") {
            then("빈 리스트를 반환한다") {
                every { tripMemberRepository.findAllWithTripByUserId(userId) } returns emptyList()

                val result = service.getList(userId)
                result shouldHaveSize 0
            }
        }
    }

    given("getDetail") {
        `when`("멤버인 경우") {
            then("여행 상세를 반환한다") {
                every { tripRepository.findByIdOrNull(tripId) } returns trip
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.VIEWER)
                every { tripMemberRepository.countByTripId(tripId) } returns 3

                val result = service.getDetail(tripId, userId)

                result.id shouldBe tripId
                result.title shouldBe "제주도 여행"
                result.memberCount shouldBe 3
                result.days shouldHaveSize 3  // 8/1 ~ 8/3 = 3일
            }
        }

        `when`("멤버가 아닌 경우") {
            then("403 FORBIDDEN을 던진다") {
                every { tripRepository.findByIdOrNull(tripId) } returns trip
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns null

                val ex = shouldThrow<ResponseStatusException> { service.getDetail(tripId, userId) }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }

        `when`("여행이 존재하지 않을 때") {
            then("404 NOT_FOUND를 던진다") {
                every { tripRepository.findByIdOrNull(tripId) } returns null

                val ex = shouldThrow<ResponseStatusException> { service.getDetail(tripId, userId) }
                ex.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    }

    given("update") {
        val request = TripRequest.Update(title = "수정된 제목", destination = null, startDate = null, endDate = null)

        `when`("EDITOR 권한일 때") {
            then("여행 정보를 수정한다") {
                every { tripRepository.findByIdOrNull(tripId) } returns trip
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)
                every { tripMemberRepository.countByTripId(tripId) } returns 2

                val result = service.update(tripId, request, userId)
                result.title shouldBe "수정된 제목"
            }
        }

        `when`("OWNER 권한일 때") {
            then("여행 정보를 수정한다") {
                val freshTrip = Trip(
                    id = tripId, owner = user, title = "원래 제목", destination = "제주도",
                    startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 3),
                )
                every { tripRepository.findByIdOrNull(tripId) } returns freshTrip
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns TripMember(trip = freshTrip, user = user, role = TripRole.OWNER)
                every { tripMemberRepository.countByTripId(tripId) } returns 1

                val result = service.update(tripId, request, userId)
                result.title shouldBe "수정된 제목"
            }
        }

        `when`("VIEWER 권한일 때") {
            then("403 FORBIDDEN을 던진다") {
                every { tripRepository.findByIdOrNull(tripId) } returns trip
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.VIEWER)

                val ex = shouldThrow<ResponseStatusException> { service.update(tripId, request, userId) }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }

        `when`("멤버가 아닐 때") {
            then("403 FORBIDDEN을 던진다") {
                every { tripRepository.findByIdOrNull(tripId) } returns trip
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns null

                val ex = shouldThrow<ResponseStatusException> { service.update(tripId, request, userId) }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }
    }

    given("delete") {
        `when`("OWNER일 때") {
            then("여행을 삭제한다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.OWNER)
                every { tripRepository.deleteById(tripId) } just Runs

                service.delete(tripId, userId)

                verify { tripRepository.deleteById(tripId) }
            }
        }

        `when`("EDITOR일 때") {
            then("403 FORBIDDEN을 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.EDITOR)

                val ex = shouldThrow<ResponseStatusException> { service.delete(tripId, userId) }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
                verify(exactly = 0) { tripRepository.deleteById(any()) }
            }
        }

        `when`("VIEWER일 때") {
            then("403 FORBIDDEN을 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.VIEWER)

                val ex = shouldThrow<ResponseStatusException> { service.delete(tripId, userId) }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }

        `when`("멤버가 아닐 때") {
            then("403 FORBIDDEN을 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns null

                val ex = shouldThrow<ResponseStatusException> { service.delete(tripId, userId) }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }
    }
})
