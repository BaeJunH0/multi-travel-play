package demo.travel.sse

import demo.travel.block.application.BlockService
import demo.travel.sse.application.PresenceStore
import demo.travel.sse.application.TripConnection
import demo.travel.sse.application.TripConnectionRegistry
import demo.travel.sse.application.TripEventPublisher
import demo.travel.sse.application.TripEventService
import demo.travel.sse.application.dto.BlockIdPayload
import demo.travel.sse.application.dto.PresencePayload
import demo.travel.sse.application.dto.PresenceUser
import demo.travel.sse.application.dto.TripEvent
import demo.travel.trip.Trip
import demo.travel.trip.TripMember
import demo.travel.trip.TripMemberRepository
import demo.travel.trip.TripRole
import demo.travel.user.AuthProvider
import demo.travel.user.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.LocalDate
import java.util.*

class TripEventServiceTest :
    BehaviorSpec({

        val eventPublisher = mockk<TripEventPublisher>(relaxed = true)
        val blockService = mockk<BlockService>()
        val tripMemberRepository = mockk<TripMemberRepository>()
        lateinit var connections: TripConnectionRegistry
        lateinit var presenceStore: PresenceStore
        lateinit var service: TripEventService

        val user = User(email = "user@test.com", nickname = "유저", provider = AuthProvider.LOCAL)
        val trip = Trip(
            owner = user,
            title = "여행",
            destination = "서울",
            startDate = LocalDate.of(2026, 8, 1),
            endDate = LocalDate.of(2026, 8, 3),
        )
        val tripId = trip.id
        val blockId = UUID.randomUUID()

        fun isPresenceWith(event: TripEvent, users: List<PresenceUser>) =
            event.action == TripEvent.Action.PRESENCE && event.payload == PresencePayload(users)

        fun isUnlockOf(event: TripEvent, id: UUID) = event.action == TripEvent.Action.UNLOCK && event.payload == BlockIdPayload(id)

        fun deadEmitter() = mockk<SseEmitter>(relaxed = true) {
            every { send(any<SseEmitter.SseEventBuilder>()) } throws IOException("broken pipe")
        }

        beforeEach {
            clearAllMocks()
            connections = TripConnectionRegistry()
            presenceStore = PresenceStore()
            service = TripEventService(connections, presenceStore, eventPublisher, blockService, tripMemberRepository)
            every { blockService.releaseLocks(any(), any()) } returns emptyList()
            every { blockService.refreshLocks(any()) } returns emptyList()
        }

        given("connect") {
            `when`("여행 멤버가 연결할 때") {
                then("연결을 등록하고 presence에 추가한 뒤 PRESENCE를 브로드캐스트한다") {
                    every { tripMemberRepository.findByTripIdAndUserId(tripId, user.id) } returns
                        TripMember(trip = trip, user = user, role = TripRole.VIEWER)

                    service.connect(tripId, user)

                    connections.of(tripId).single().userId shouldBe user.id
                    val expected = PresenceUser(user.id, "유저", PresenceUser.avatarColorOf(user.id), null)
                    presenceStore.getUsers(tripId) shouldBe listOf(expected)
                    verify(exactly = 1) { eventPublisher.publish(tripId, match { isPresenceWith(it, listOf(expected)) }) }
                }
            }

            `when`("멤버가 아닐 때") {
                then("403을 던지고 연결을 등록하지 않는다") {
                    every { tripMemberRepository.findByTripIdAndUserId(tripId, user.id) } returns null

                    val ex = shouldThrow<ResponseStatusException> { service.connect(tripId, user) }
                    ex.statusCode shouldBe HttpStatus.FORBIDDEN
                    connections.all().shouldBeEmpty()
                    presenceStore.getUsers(tripId).shouldBeEmpty()
                }
            }
        }

        given("updatePresence") {
            `when`("접속 중인 유저일 때") {
                then("activeDay를 바꾸고 PRESENCE를 브로드캐스트한다") {
                    presenceStore.join(tripId, PresenceUser.of(user))

                    service.updatePresence(tripId, user.id, 2)

                    presenceStore.getUsers(tripId).single().activeDay shouldBe 2
                    verify(exactly = 1) { eventPublisher.publish(tripId, match { it.action == TripEvent.Action.PRESENCE }) }
                }
            }

            `when`("접속 중이 아닌 유저일 때") {
                then("아무것도 발행하지 않는다") {
                    service.updatePresence(tripId, user.id, 2)

                    verify(exactly = 0) { eventPublisher.publish(any(), any()) }
                }
            }
        }

        given("disconnect") {
            `when`("유저의 마지막 연결이 끊길 때") {
                then("presence에서 빼고, 잡고 있던 잠금을 해제해 UNLOCK을 블록마다 브로드캐스트한다") {
                    val connection = TripConnection(tripId, user.id)
                    connections.add(connection)
                    presenceStore.join(tripId, PresenceUser.of(user))
                    every { blockService.releaseLocks(tripId, user.id) } returns listOf(blockId)

                    service.disconnect(connection)

                    presenceStore.getUsers(tripId).shouldBeEmpty()
                    verify(exactly = 1) { eventPublisher.publish(tripId, match { isPresenceWith(it, emptyList()) }) }
                    verify(exactly = 1) { eventPublisher.publish(tripId, match { isUnlockOf(it, blockId) }) }
                }
            }

            `when`("같은 유저의 다른 탭 연결이 남아 있을 때") {
                then("presence와 잠금을 그대로 둔다") {
                    val tab1 = TripConnection(tripId, user.id)
                    connections.add(tab1)
                    connections.add(TripConnection(tripId, user.id))
                    presenceStore.join(tripId, PresenceUser.of(user))

                    service.disconnect(tab1)

                    presenceStore.getUsers(tripId).single().userId shouldBe user.id
                    verify(exactly = 0) { blockService.releaseLocks(any(), any()) }
                    verify(exactly = 0) { eventPublisher.publish(any(), any()) }
                }
            }

            `when`("같은 연결의 종료 콜백이 두 번 올 때 (onError 후 onCompletion)") {
                then("한 번만 처리한다") {
                    val connection = TripConnection(tripId, user.id)
                    connections.add(connection)

                    service.disconnect(connection)
                    service.disconnect(connection)

                    verify(exactly = 1) { blockService.releaseLocks(tripId, user.id) }
                }
            }
        }

        given("heartbeat") {
            `when`("heartbeat 전송에 실패한 연결이 있을 때") {
                then("끊긴 연결로 처리해 잠금을 해제하고, 살아 있는 연결만 잠금 연장 대상으로 넘긴다") {
                    val otherId = UUID.randomUUID()
                    connections.add(TripConnection(tripId, user.id, deadEmitter()))
                    connections.add(TripConnection(tripId, otherId))
                    every { blockService.releaseLocks(tripId, user.id) } returns listOf(blockId)

                    service.heartbeat()

                    connections.of(tripId).single().userId shouldBe otherId
                    verify(exactly = 1) { eventPublisher.publish(tripId, match { isUnlockOf(it, blockId) }) }
                    verify(exactly = 1) { blockService.refreshLocks(setOf(tripId to otherId)) }
                }
            }

            `when`("만료되어 해제된 잠금이 있을 때") {
                then("해당 여행에 UNLOCK을 브로드캐스트한다") {
                    val expiredBlockId = UUID.randomUUID()
                    every { blockService.refreshLocks(emptySet()) } returns listOf(tripId to expiredBlockId)

                    service.heartbeat()

                    verify(exactly = 1) { eventPublisher.publish(tripId, match { isUnlockOf(it, expiredBlockId) }) }
                }
            }
        }
    })
