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
class WebSocketConfigTest {

    @LocalServerPort
    private var port: Int = 0

    private val httpClient = HttpClient.newHttpClient()

    private fun sockJsInfo(origin: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port/ws/info"))
            .header("Origin", origin)
            .GET()
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `허용된 프론트엔드 origin의 SockJS 핸드셰이크는 차단되지 않는다`() {
        val response = sockJsInfo("http://localhost:5173")

        assertEquals(200, response.statusCode())
    }

    @Test
    fun `허용 목록에 없는 origin은 여전히 차단된다`() {
        val response = sockJsInfo("http://evil.example.com")

        assertEquals(403, response.statusCode())
    }
}
