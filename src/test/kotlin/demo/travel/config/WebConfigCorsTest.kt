package demo.travel.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebConfigCorsTest {

    @LocalServerPort
    private var port: Int = 0

    private val httpClient = HttpClient.newHttpClient()

    private fun preflight(origin: String, method: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port/api/trips"))
            .header("Origin", origin)
            .header("Access-Control-Request-Method", method)
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `허용된 프론트엔드 origin의 REST preflight는 통과하고 쿠키 전송이 허용된다`() {
        val response = preflight("http://localhost:5173", "PATCH")

        assertEquals(200, response.statusCode())
        assertEquals("http://localhost:5173", response.headers().firstValue("Access-Control-Allow-Origin").orElse(null))
        assertEquals("true", response.headers().firstValue("Access-Control-Allow-Credentials").orElse(null))
    }

    @Test
    fun `허용 목록에 없는 origin의 REST preflight는 차단된다`() {
        val response = preflight("http://evil.example.com", "PATCH")

        assertEquals(403, response.statusCode())
    }
}
