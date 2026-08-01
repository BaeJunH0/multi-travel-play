package demo.travel.invite

import demo.travel.trip.Trip
import demo.travel.trip.TripMember
import demo.travel.trip.TripMemberRepository
import demo.travel.trip.TripRepository
import demo.travel.trip.TripRole
import demo.travel.user.AuthProvider
import demo.travel.user.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.*

class InviteServiceTest : BehaviorSpec({

    val tripRepository = mockk<TripRepository>()
    val tripMemberRepository = mockk<TripMemberRepository>()
    val service = InviteService(tripRepository, tripMemberRepository)

    val userId = UUID.randomUUID()
    val guestId = UUID.randomUUID()
    val tripId = UUID.randomUUID()
    val baseUrl = "https://travel.example.com"

    val user = User(id = userId, email = "owner@test.com", nickname = "오너", provider = AuthProvider.LOCAL)
    val guest = User(id = guestId, email = "guest@test.com", nickname = "게스트", provider = AuthProvider.LOCAL)
    val tripWithoutToken = Trip(
        id = tripId, owner = user, title = "제주도 여행", destination = "제주도",
        startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 3),
        shareToken = null,
    )
    val tripWithToken = Trip(
        id = tripId, owner = user, title = "제주도 여행", destination = "제주도",
        startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 3),
        shareToken = "existingtoken1234",
    )

    fun memberWith(role: TripRole, t: Trip = tripWithoutToken) = TripMember(trip = t, user = user, role = role)

    beforeEach { clearAllMocks() }

    given("createInviteLink") {
        `when`("EDITOR 권한이고 shareToken이 없을 때") {
            then("새 토큰을 생성하고 InviteLinkResponse를 반환한다") {
                val editorMember = memberWith(TripRole.EDITOR)
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns editorMember

                val result = service.createInviteLink(tripId, userId, baseUrl)

                result.shareToken shouldNotBe null
                result.shareToken.length shouldBe 16
                result.inviteUrl shouldBe "$baseUrl/invite/${result.shareToken}"
                // trip.shareToken이 설정됐는지 확인
                editorMember.trip.shareToken shouldBe result.shareToken
            }
        }

        `when`("이미 shareToken이 있을 때") {
            then("기존 토큰을 재사용한다") {
                val editorMember = TripMember(trip = tripWithToken, user = user, role = TripRole.EDITOR)
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns editorMember

                val result = service.createInviteLink(tripId, userId, baseUrl)

                result.shareToken shouldBe "existingtoken1234"
                result.inviteUrl shouldBe "$baseUrl/invite/existingtoken1234"
            }
        }

        `when`("VIEWER 권한일 때") {
            then("403 FORBIDDEN을 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns memberWith(TripRole.VIEWER)

                val ex = shouldThrow<ResponseStatusException> { service.createInviteLink(tripId, userId, baseUrl) }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }

        `when`("멤버가 아닐 때") {
            then("403 FORBIDDEN을 던진다") {
                every { tripMemberRepository.findByTripIdAndUserId(tripId, userId) } returns null

                val ex = shouldThrow<ResponseStatusException> { service.createInviteLink(tripId, userId, baseUrl) }
                ex.statusCode shouldBe HttpStatus.FORBIDDEN
            }
        }
    }

    given("getInviteInfo") {
        val shareToken = "existingtoken1234"

        `when`("유효한 shareToken일 때") {
            then("여행 미리보기 정보를 반환한다") {
                val ownerMember = TripMember(trip = tripWithToken, user = user, role = TripRole.OWNER)
                every { tripRepository.findByShareToken(shareToken) } returns tripWithToken
                every { tripMemberRepository.countByTripId(tripId) } returns 2
                every { tripMemberRepository.findByTripIdAndRole(tripId, TripRole.OWNER) } returns ownerMember

                val result = service.getInviteInfo(shareToken)

                result.tripId shouldBe tripId
                result.tripTitle shouldBe "제주도 여행"
                result.memberCount shouldBe 2
                result.inviterNickname shouldBe "오너"
            }
        }

        `when`("존재하지 않는 shareToken일 때") {
            then("404 NOT_FOUND를 던진다") {
                every { tripRepository.findByShareToken(shareToken) } returns null

                val ex = shouldThrow<ResponseStatusException> { service.getInviteInfo(shareToken) }
                ex.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    }

    given("accept") {
        val shareToken = "existingtoken1234"

        `when`("신규 사용자가 초대를 수락할 때") {
            then("VIEWER로 TripMember를 저장하고 tripId를 반환한다") {
                val newMember = TripMember(trip = tripWithToken, user = guest, role = TripRole.VIEWER)
                every { tripRepository.findByShareToken(shareToken) } returns tripWithToken
                every { tripMemberRepository.findByTripIdAndUserId(tripId, guestId) } returns null
                every { tripMemberRepository.save(any()) } returns newMember

                val result = service.accept(shareToken, guest)

                result shouldBe tripId
                verify { tripMemberRepository.save(match { it.role == TripRole.VIEWER && it.user.id == guestId }) }
            }
        }

        `when`("이미 멤버인 사용자가 다시 수락할 때") {
            then("저장 없이 tripId를 반환한다 (멱등성)") {
                val existingMember = TripMember(trip = tripWithToken, user = guest, role = TripRole.EDITOR)
                every { tripRepository.findByShareToken(shareToken) } returns tripWithToken
                every { tripMemberRepository.findByTripIdAndUserId(tripId, guestId) } returns existingMember

                val result = service.accept(shareToken, guest)

                result shouldBe tripId
                verify(exactly = 0) { tripMemberRepository.save(any()) }
            }
        }

        `when`("존재하지 않는 shareToken일 때") {
            then("404 NOT_FOUND를 던진다") {
                every { tripRepository.findByShareToken(shareToken) } returns null

                val ex = shouldThrow<ResponseStatusException> { service.accept(shareToken, guest) }
                ex.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    }
})
