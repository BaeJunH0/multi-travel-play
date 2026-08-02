package demo.travel.block

import demo.travel.common.TripCategory
import demo.travel.trip.Trip
import demo.travel.trip.TripRepository
import demo.travel.user.AuthProvider
import demo.travel.user.User
import demo.travel.user.UserRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import java.time.LocalDate

@SpringBootTest
class BlockRepositoryTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var tripRepository: TripRepository
    @Autowired lateinit var blockRepository: BlockRepository

    private fun savedUser(suffix: String) =
        userRepository.save(User(email = "br-$suffix@test.com", nickname = "유저$suffix", provider = AuthProvider.LOCAL))

    private fun savedTrip(owner: User) = tripRepository.save(
        Trip(
            owner = owner, title = "여행", destination = "서울",
            startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 3),
        )
    )

    private fun savedBlock(trip: Trip, creator: User, dayNumber: Int, position: Double) =
        blockRepository.save(
            ScheduleBlock(
                trip = trip, dayNumber = dayNumber, position = position,
                blockType = TripCategory.PLACE, placeName = "장소", createdBy = creator,
            )
        )

    init {
        given("findAllByTripIdOrderByDayNumberAscPositionAsc") {
            `when`("여행에 여러 day의 블록이 있을 때") {
                then("dayNumber 오름차순, 같은 day 내에서 position 오름차순으로 반환한다") {
                    val user = savedUser("ord1")
                    val trip = savedTrip(user)
                    savedBlock(trip, user, dayNumber = 2, position = 1.0)
                    savedBlock(trip, user, dayNumber = 1, position = 2.0)
                    savedBlock(trip, user, dayNumber = 1, position = 1.0)

                    val result = blockRepository.findAllByTripIdOrderByDayNumberAscPositionAsc(trip.id)

                    result shouldHaveSize 3
                    result[0].dayNumber shouldBe 1
                    result[0].position shouldBe 1.0
                    result[1].dayNumber shouldBe 1
                    result[1].position shouldBe 2.0
                    result[2].dayNumber shouldBe 2
                }
            }

            `when`("여행에 블록이 없을 때") {
                then("빈 리스트를 반환한다") {
                    val user = savedUser("ord2")
                    val trip = savedTrip(user)

                    val result = blockRepository.findAllByTripIdOrderByDayNumberAscPositionAsc(trip.id)

                    result shouldHaveSize 0
                }
            }

            `when`("다른 여행의 블록이 섞여 있을 때") {
                then("해당 여행의 블록만 반환한다") {
                    val user = savedUser("ord3")
                    val trip1 = savedTrip(user)
                    val trip2 = tripRepository.save(
                        Trip(
                            owner = user, title = "다른 여행", destination = "부산",
                            startDate = LocalDate.of(2026, 9, 1), endDate = LocalDate.of(2026, 9, 2),
                        )
                    )
                    savedBlock(trip1, user, dayNumber = 1, position = 1.0)
                    savedBlock(trip2, user, dayNumber = 1, position = 1.0)

                    val result = blockRepository.findAllByTripIdOrderByDayNumberAscPositionAsc(trip1.id)

                    result shouldHaveSize 1
                    result[0].trip.id shouldBe trip1.id
                }
            }
        }

        given("findTopByTripIdAndDayNumberOrderByPositionDesc") {
            `when`("해당 day에 블록이 여러 개 있을 때") {
                then("position이 가장 큰 블록을 반환한다") {
                    val user = savedUser("top1")
                    val trip = savedTrip(user)
                    savedBlock(trip, user, dayNumber = 1, position = 1.0)
                    savedBlock(trip, user, dayNumber = 1, position = 3.0)
                    savedBlock(trip, user, dayNumber = 1, position = 2.0)

                    val result = blockRepository.findTopByTripIdAndDayNumberOrderByPositionDesc(trip.id, 1)

                    result shouldNotBe null
                    result!!.position shouldBe 3.0
                }
            }

            `when`("해당 day에 블록이 없을 때") {
                then("null을 반환한다") {
                    val user = savedUser("top2")
                    val trip = savedTrip(user)

                    val result = blockRepository.findTopByTripIdAndDayNumberOrderByPositionDesc(trip.id, 1)

                    result shouldBe null
                }
            }

            `when`("dayNumber가 다른 블록이 있을 때") {
                then("지정한 day의 블록만 대상으로 한다") {
                    val user = savedUser("top3")
                    val trip = savedTrip(user)
                    savedBlock(trip, user, dayNumber = 1, position = 5.0)
                    savedBlock(trip, user, dayNumber = 2, position = 1.0)

                    val result = blockRepository.findTopByTripIdAndDayNumberOrderByPositionDesc(trip.id, 2)

                    result shouldNotBe null
                    result!!.position shouldBe 1.0
                }
            }
        }

        given("lockedBy 변경") {
            `when`("lockedBy만 변경하고 저장할 때") {
                then("version이 증가하지 않는다") {
                    val user = savedUser("lock1")
                    val trip = savedTrip(user)
                    val block = savedBlock(trip, user, dayNumber = 1, position = 1.0)
                    val versionBefore = block.version

                    block.lockedBy = user
                    blockRepository.saveAndFlush(block)

                    val reloaded = blockRepository.findByIdOrNull(block.id)!!
                    reloaded.version shouldBe versionBefore
                }
            }
        }
    }
}
