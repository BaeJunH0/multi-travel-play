package demo.travel.auth.application

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 비밀번호 재설정 인증 코드를 서버 메모리에 [TTL] 동안 보관한다.
 * 서버를 재시작하면 발송된 코드는 사라진다 (단일 인스턴스 전제).
 */
@Component
class PasswordResetCodeStore(
    private val clock: Clock = Clock.systemUTC(),
) {
    private data class Entry(val code: String, val expiresAt: Instant)

    private val codes = ConcurrentHashMap<String, Entry>()

    fun save(email: String, code: String) {
        val now = clock.instant()
        codes.values.removeIf { !now.isBefore(it.expiresAt) }
        codes[email] = Entry(code, now.plus(TTL))
    }

    /** 만료되지 않은 코드만 반환한다. */
    fun find(email: String): String? {
        val entry = codes[email] ?: return null
        if (clock.instant().isBefore(entry.expiresAt)) return entry.code
        codes.remove(email, entry)
        return null
    }

    fun remove(email: String) {
        codes.remove(email)
    }

    companion object {
        val TTL: Duration = Duration.ofMinutes(15)
    }
}
