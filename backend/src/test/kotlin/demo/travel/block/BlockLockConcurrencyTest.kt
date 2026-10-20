package demo.travel.block

import demo.travel.block.application.BlockService
import demo.travel.common.TripCategory
import demo.travel.trip.Trip
import demo.travel.trip.TripMember
import demo.travel.trip.TripMemberRepository
import demo.travel.trip.TripRepository
import demo.travel.trip.TripRole
import demo.travel.user.AuthProvider
import demo.travel.user.User
import demo.travel.user.UserRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * heartbeat의 잠금 연장·해제와 다른 요청이 같은 블록을 동시에 바꿀 때를 재현한다.
 * 바깥 트랜잭션이 잠긴 블록을 먼저 읽어 둔 상태에서, 별도 트랜잭션(REQUIRES_NEW)이 같은 블록을 바꾸고 커밋한 뒤
 * 바깥 트랜잭션에서 잠금 연장·해제를 실행하고 커밋한다.
 */
@SpringBootTest
class BlockLockConcurrencyTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var tripRepository: TripRepository

    @Autowired lateinit var tripMemberRepository: TripMemberRepository

    @Autowired lateinit var blockRepository: BlockRepository

    @Autowired lateinit var blockService: BlockService

    @Autowired lateinit var transactionManager: PlatformTransactionManager

    private fun savedUser(suffix: String) =
        userRepository.save(User(email = "blc-$suffix@test.com", nickname = "유저$suffix", provider = AuthProvider.LOCAL))

    private fun savedTrip(owner: User, editor: User): Trip {
        val trip = tripRepository.save(
            Trip(
                owner = owner,
                title = "여행",
                destination = "서울",
                startDate = LocalDate.of(2026, 8, 1),
                endDate = LocalDate.of(2026, 8, 3),
            ),
        )
        tripMemberRepository.save(TripMember(trip = trip, user = owner, role = TripRole.OWNER))
        tripMemberRepository.save(TripMember(trip = trip, user = editor, role = TripRole.EDITOR))
        return trip
    }

    private fun savedLockedBlock(trip: Trip, locker: User, lockExpiresAt: LocalDateTime) = blockRepository.save(
        ScheduleBlock(
            trip = trip,
            dayNumber = 1,
            position = 1.0,
            blockType = TripCategory.PLACE,
            placeName = "A",
            createdBy = locker,
            lockedBy = locker,
            lockExpiresAt = lockExpiresAt,
        ),
    )

    /** 바깥 트랜잭션에서 잠긴 블록을 먼저 읽고, [concurrent]를 별도 트랜잭션으로 커밋한 뒤 [action]을 실행한다. */
    private fun <T> interleaved(concurrent: () -> Unit, action: () -> T): T {
        val outer = TransactionTemplate(transactionManager)
        val inner = TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
        return outer.execute {
            blockRepository.findAllByLockedByIsNotNull() // heartbeat가 먼저 읽어 둔 상태
            inner.executeWithoutResult { concurrent() }
            action()
        }!!
    }

    init {
        given("refreshLocks (잠금 연장)") {
            `when`("읽은 뒤 잠금자가 같은 블록을 수정해 커밋했을 때") {
                then("수정 내용을 덮어쓰지 않고 만료만 연장한다") {
                    val locker = savedUser("ext1")
                    val trip = savedTrip(locker, savedUser("ext1o"))
                    val block = savedLockedBlock(trip, locker, LocalDateTime.now().plusSeconds(30))

                    interleaved(
                        concurrent = { blockRepository.findByIdOrNull(block.id)!!.placeName = "B" },
                        action = { blockService.refreshLocks(setOf(trip.id to locker.id)) },
                    )

                    val reloaded = blockRepository.findByIdOrNull(block.id)!!
                    reloaded.placeName shouldBe "B"
                    reloaded.lockExpiresAt!!.isAfter(LocalDateTime.now().plusMinutes(2)) shouldBe true
                }
            }
        }

        given("refreshLocks (만료 해제)") {
            `when`("읽은 뒤 다른 사용자가 만료된 잠금을 새로 잡아 커밋했을 때") {
                then("새 잠금을 해제하지 않고, 해제 목록에도 넣지 않는다") {
                    val oldLocker = savedUser("exp1")
                    val newLocker = savedUser("exp1n")
                    val trip = savedTrip(oldLocker, newLocker)
                    val block = savedLockedBlock(trip, oldLocker, LocalDateTime.now().minusMinutes(1))

                    val released = interleaved(
                        concurrent = { blockService.lockBlock(trip.id, block.id, newLocker.id) },
                        action = { blockService.refreshLocks(emptySet()) },
                    )

                    released.none { it.second == block.id } shouldBe true
                    val reloaded = blockRepository.findByIdOrNull(block.id)!!
                    reloaded.lockedBy?.id shouldBe newLocker.id
                    reloaded.lockExpiresAt shouldNotBe null
                }
            }
        }

        given("releaseLocks (연결 종료)") {
            `when`("읽은 뒤 같은 블록의 내용이 수정돼 커밋됐을 때") {
                then("수정 내용을 덮어쓰지 않고 잠금만 해제한다") {
                    val locker = savedUser("rel1")
                    val trip = savedTrip(locker, savedUser("rel1o"))
                    val block = savedLockedBlock(trip, locker, LocalDateTime.now().plusMinutes(1))

                    val released = interleaved(
                        concurrent = { blockRepository.findByIdOrNull(block.id)!!.placeName = "B" },
                        action = { blockService.releaseLocks(trip.id, locker.id) },
                    )

                    released shouldBe listOf(block.id)
                    val reloaded = blockRepository.findByIdOrNull(block.id)!!
                    reloaded.placeName shouldBe "B"
                    reloaded.lockedBy shouldBe null
                }
            }
        }
    }
}
