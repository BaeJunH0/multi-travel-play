package demo.travel.auth.application

import demo.travel.config.JwtProperties
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID

@Component
class JwtProvider(props: JwtProperties) {
    private val key = Keys.hmacShaKeyFor(props.secret.toByteArray())
    private val accessTokenExpiry = props.accessTokenExpiry
    private val refreshTokenExpiry = props.refreshTokenExpiry

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
