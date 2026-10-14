package demo.travel.sse

import demo.travel.auth.application.JwtProvider
import demo.travel.trip.Trip
import demo.travel.trip.TripMember
import demo.travel.trip.TripMemberRepository
import demo.travel.trip.TripRepository
import demo.travel.trip.TripRole
import demo.travel.user.AuthProvider
import demo.travel.user.User
import demo.travel.user.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.util.*

/** 실제 HTTP로 SSE 엔드포인트의 인증·권한과 첫 이벤트(PRESENCE)를 확인한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TripEventControllerTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var tripRepository: TripRepository
    @Autowired lateinit var tripMemberRepository: TripMemberRepository
    @Autowired lateinit var jwtProvider: JwtProvider

    private val httpClient = HttpClient.newHttpClient()

    private fun savedUser(suffix: String) =
        userRepository.save(User(email = "sse-$suffix@test.com", nickname = "유저$suffix", provider = AuthProvider.LOCAL))

    private fun savedTrip(owner: User): Trip {
        val trip = tripRepository.save(
            Trip(
                owner = owner, title = "여행", destination = "서울",
                startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 3),
            )
        )
        tripMemberRepository.save(TripMember(trip = trip, user = owner, role = TripRole.OWNER))
        return trip
    }

    private fun connect(tripId: UUID, token: String?): HttpResponse<java.util.stream.Stream<String>> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port/api/trips/$tripId/events"))
            .header("Accept", "text/event-stream")
            .timeout(Duration.ofSeconds(5))
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .GET()
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofLines())
    }

    @Test
    fun `멤버가 연결하면 text event-stream으로 자신이 포함된 PRESENCE를 받는다`() {
        val owner = savedUser("member")
        val trip = savedTrip(owner)

        val response = connect(trip.id, jwtProvider.generate(owner.id))

        response.body().use { lines ->
            assertEquals(200, response.statusCode())
            assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"))
            val data = lines.filter { it.startsWith("data:") }.findFirst().orElseThrow()
            assertTrue(data.contains("\"action\":\"PRESENCE\""))
            assertTrue(data.contains(owner.id.toString()))
        }
    }

    @Test
    fun `멤버가 아니면 403`() {
        val trip = savedTrip(savedUser("owner"))
        val stranger = savedUser("stranger")

        val response = connect(trip.id, jwtProvider.generate(stranger.id))

        response.body().close()
        assertEquals(403, response.statusCode())
    }

    @Test
    fun `토큰이 없으면 401`() {
        val trip = savedTrip(savedUser("anon"))

        val response = connect(trip.id, token = null)

        response.body().close()
        assertEquals(401, response.statusCode())
    }
}
