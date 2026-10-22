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
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

/** 재정렬이 돌려주는 version이 실제 DB에서 올라간 version과 같은지 확인한다. (MockK로는 @Version 증가를 볼 수 없다) */
@SpringBootTest
class BlockReorderPersistenceTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var tripRepository: TripRepository

    @Autowired lateinit var tripMemberRepository: TripMemberRepository

    @Autowired lateinit var blockRepository: BlockRepository

    @Autowired lateinit var blockService: BlockService

    init {
        given("blockService.reorderBlocks") {
            `when`("Day 1의 position 간격이 좁아져 있을 때") {
                then("#85-TC-2 바뀐 블록만 DB에 저장된 position·version으로 돌려주고 그대로인 블록의 version은 유지한다") {
                    val user = userRepository.save(User(email = "reorder-sync@test.com", nickname = "유저", provider = AuthProvider.LOCAL))
                    val trip = tripRepository.save(
                        Trip(
                            owner = user,
                            title = "여행",
                            destination = "서울",
                            startDate = LocalDate.of(2026, 8, 1),
                            endDate = LocalDate.of(2026, 8, 3),
                        ),
                    )
                    tripMemberRepository.save(TripMember(trip = trip, user = user, role = TripRole.EDITOR))
                    fun saved(position: Double) = blockRepository.save(
                        ScheduleBlock(
                            trip = trip,
                            dayNumber = 1,
                            position = position,
                            blockType = TripCategory.PLACE,
                            placeName = "장소",
                            createdBy = user,
                        ),
                    )
                    val stay = saved(1.0)
                    val second = saved(1.00005)
                    val third = saved(1.0001)
                    // 재정렬 전 DB에 저장된 version
                    val before = listOf(stay, second, third).associate { it.id to blockRepository.findById(it.id).get().version }

                    val results = blockService.reorderBlocks(trip.id, user.id)

                    results.map { it.id } shouldContainExactlyInAnyOrder listOf(second.id, third.id)
                    results.forEach { result ->
                        val stored = blockRepository.findById(result.id).get()
                        result.position shouldBe stored.position
                        result.version shouldBe stored.version
                        stored.version shouldBeGreaterThan before.getValue(result.id)
                    }
                    blockRepository.findById(stay.id).get().version shouldBe before.getValue(stay.id)
                }
            }
        }
    }
}
