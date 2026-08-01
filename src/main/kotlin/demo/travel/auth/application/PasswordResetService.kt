package demo.travel.auth.application

import demo.travel.auth.presentation.dto.PasswordResetRequest
import demo.travel.common.exception.BusinessException
import demo.travel.user.AuthProvider
import demo.travel.user.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Service
class PasswordResetService(
    private val userRepository: UserRepository,
    private val jwtProvider: JwtProvider,
    private val redisTemplate: StringRedisTemplate,
    @Autowired(required = false) private val mailSender: JavaMailSender?,
) {
    private val passwordEncoder = BCryptPasswordEncoder()

    fun requestReset(email: String) {
        // 사용자 존재 여부와 무관하게 같은 응답 (이메일 열거 공격 방지)
        val user = userRepository.findByEmail(email) ?: return

        val code = (100_000..999_999).random().toString()
        redisTemplate.opsForValue().set(resetKey(email), code, 15, TimeUnit.MINUTES)

        val message = SimpleMailMessage().apply {
            setTo(email)
            subject = "[TripPlanner] 비밀번호 재설정 인증 코드"
            text = """
                안녕하세요, ${user.nickname}님!

                비밀번호 재설정 인증 코드: $code

                이 코드는 15분간 유효합니다.
                본인이 요청하지 않았다면 이 이메일을 무시하세요.
            """.trimIndent()
        }
        try {
            mailSender?.send(message)
        } catch (e: Exception) {
            // 메일 전송 실패 시 토큰은 이미 저장됐으므로 로그만 기록
        }
    }

    @Transactional
    fun confirmReset(request: PasswordResetRequest.Confirm): String {
        val user = userRepository.findByEmail(request.email)
            ?: throw BusinessException("USER_NOT_FOUND", "존재하지 않는 이메일입니다.")

        if (user.provider != AuthProvider.LOCAL) {
            throw BusinessException("SOCIAL_ACCOUNT", "소셜 로그인 계정은 비밀번호를 변경할 수 없습니다.")
        }

        val stored = redisTemplate.opsForValue().get(resetKey(request.email))
        if (stored == null || stored != request.token) {
            throw BusinessException("INVALID_RESET_TOKEN", "인증 코드가 올바르지 않거나 만료됐습니다.")
        }

        user.changePassword(passwordEncoder.encode(request.newPassword)!!)
        redisTemplate.delete(resetKey(request.email))

        return jwtProvider.generate(user.id)
    }

    private fun resetKey(email: String) = "password-reset:$email"
}