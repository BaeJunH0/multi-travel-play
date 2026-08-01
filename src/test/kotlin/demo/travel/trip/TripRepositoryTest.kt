package demo.travel.trip

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
import java.time.LocalDate

@SpringBootTest
class TripRepositoryTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var tripRepository: TripRepository
    @Autowired lateinit var tripMemberRepository: TripMemberRepository

    private fun savedUser(suffix: String) =
        userRepository.save(User(email = "tr-$suffix@test.com", nickname = "유저$suffix", provider = AuthProvider.LOCAL))

    private fun savedTrip(owner: User, shareToken: String? = null) =
        tripRepository.save(
            Trip(
                owner = owner, title = "여행", destination = "서울",
                startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 3),
                shareToken = shareToken,
            )
        )

    init {
        given("TripRepository.findByShareToken") {
            `when`("해당 토큰을 가진 여행이 존재할 때") {
                then("Trip을 반환한다") {
                    val user = savedUser("st1")
                    savedTrip(user, shareToken = "tr-token-abc123")

                    val result = tripRepository.findByShareToken("tr-token-abc123")

                    result shouldNotBe null
                    result!!.shareToken shouldBe "tr-token-abc123"
                }
            }

            `when`("해당 토큰을 가진 여행이 없을 때") {
                then("null을 반환한다") {
                    val result = tripRepository.findByShareToken("tr-nonexistent-token")

                    result shouldBe null
                }
            }
        }

        given("TripMemberRepository.findByTripIdAndUserId") {
            `when`("해당 (trip, user) 조합의 멤버가 존재할 때") {
                then("TripMember를 반환한다") {
                    val user = savedUser("tm1")
                    val trip = savedTrip(user)
                    tripMemberRepository.save(TripMember(trip = trip, user = user, role = TripRole.OWNER))

                    val result = tripMemberRepository.findByTripIdAndUserId(trip.id, user.id)

                    result shouldNotBe null
                    result!!.role shouldBe TripRole.OWNER
                }
            }

            `when`("해당 (trip, user) 조합의 멤버가 없을 때") {
                then("null을 반환한다") {
                    val user = savedUser("tm2")
                    val trip = savedTrip(user)

                    val result = tripMemberRepository.findByTripIdAndUserId(trip.id, user.id)

                    result shouldBe null
                }
            }
        }

        given("TripMemberRepository.findByTripIdAndRole") {
            `when`("해당 role의 멤버가 존재할 때") {
                then("TripMember를 반환한다") {
                    val owner = savedUser("role1")
                    val trip = savedTrip(owner)
                    tripMemberRepository.save(TripMember(trip = trip, user = owner, role = TripRole.OWNER))

                    val result = tripMemberRepository.findByTripIdAndRole(trip.id, TripRole.OWNER)

                    result shouldNotBe null
                    result!!.user.id shouldBe owner.id
                }
            }

            `when`("해당 role의 멤버가 없을 때") {
                then("null을 반환한다") {
                    val owner = savedUser("role2")
                    val trip = savedTrip(owner)
                    tripMemberRepository.save(TripMember(trip = trip, user = owner, role = TripRole.OWNER))

                    val result = tripMemberRepository.findByTripIdAndRole(trip.id, TripRole.EDITOR)

                    result shouldBe null
                }
            }
        }

        given("TripMemberRepository.countByTripId") {
            `when`("멤버가 3명인 여행") {
                then("3을 반환한다") {
                    val owner = savedUser("cnt1")
                    val editor = savedUser("cnt2")
                    val viewer = savedUser("cnt3")
                    val trip = savedTrip(owner)
                    tripMemberRepository.save(TripMember(trip = trip, user = owner, role = TripRole.OWNER))
                    tripMemberRepository.save(TripMember(trip = trip, user = editor, role = TripRole.EDITOR))
                    tripMemberRepository.save(TripMember(trip = trip, user = viewer, role = TripRole.VIEWER))

                    val count = tripMemberRepository.countByTripId(trip.id)

                    count shouldBe 3
                }
            }
        }

        given("TripMemberRepository.findAllByTripId") {
            `when`("여행에 멤버가 2명일 때") {
                then("2개의 TripMember를 반환한다") {
                    val owner = savedUser("all1")
                    val editor = savedUser("all2")
                    val trip = savedTrip(owner)
                    tripMemberRepository.save(TripMember(trip = trip, user = owner, role = TripRole.OWNER))
                    tripMemberRepository.save(TripMember(trip = trip, user = editor, role = TripRole.EDITOR))

                    val result = tripMemberRepository.findAllByTripId(trip.id)

                    result shouldHaveSize 2
                }
            }
        }

        given("TripMemberRepository.findAllWithTripByUserId") {
            `when`("사용자가 2개의 여행에 속해 있을 때") {
                then("Trip이 JOIN FETCH된 TripMember 2개를 반환한다") {
                    val user = savedUser("fetch1")
                    val trip1 = savedTrip(user)
                    val trip2 = tripRepository.save(
                        Trip(
                            owner = user, title = "두 번째 여행", destination = "부산",
                            startDate = LocalDate.of(2026, 9, 1), endDate = LocalDate.of(2026, 9, 3),
                        )
                    )
                    tripMemberRepository.save(TripMember(trip = trip1, user = user, role = TripRole.OWNER))
                    tripMemberRepository.save(TripMember(trip = trip2, user = user, role = TripRole.EDITOR))

                    val result = tripMemberRepository.findAllWithTripByUserId(user.id)

                    result shouldHaveSize 2
                    result.map { it.trip.title }.toSet() shouldBe setOf("여행", "두 번째 여행")
                }
            }
        }
    }
}
