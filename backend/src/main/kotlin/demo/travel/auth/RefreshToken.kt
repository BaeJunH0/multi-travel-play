package demo.travel.auth

import jakarta.persistence.*
import java.util.*

/** 유저당 하나만 유지되는 refresh token. 새로 로그인하면 덮어쓰고, 로그아웃하면 삭제한다. */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    val userId: UUID,

    @Column(nullable = false, length = 512)
    var token: String,
)
