package demo.travel.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "users")
class User(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(unique = true, nullable = false, length = 255)
    val email: String,

    @Column(nullable = false, length = 100)
    var nickname: String,

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    val provider: AuthProvider,

    @Column(length = 255)
    var password: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    fun changePassword(encoded: String) {
        password = encoded
    }
}

enum class AuthProvider { LOCAL, KAKAO, GOOGLE }
