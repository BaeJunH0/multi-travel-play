package demo.travel.auth

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

@Component
class JwtProvider(
    @Value("\${jwt.secret}") secret: String,
    @Value("\${jwt.access-token-expiry}") private val accessTokenExpiry: Long,
    @Value("\${jwt.refresh-token-expiry}") private val refreshTokenExpiry: Long,
) {
    private val key = Keys.hmacShaKeyFor(secret.toByteArray())

    fun generate(userId: UUID): String = Jwts.builder()
        .subject(userId.toString())
        .issuedAt(Date())
        .expiration(Date(System.currentTimeMillis() + accessTokenExpiry))
        .signWith(key)
        .compact()

    fun generateRefresh(userId: UUID): String = Jwts.builder()
        .subject(userId.toString())
        .issuedAt(Date())
        .expiration(Date(System.currentTimeMillis() + refreshTokenExpiry))
        .signWith(key)
        .compact()

    fun parse(token: String): UUID? = try {
        val subject = Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).payload.subject
        UUID.fromString(subject)
    } catch (e: JwtException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
