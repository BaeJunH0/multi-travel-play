# auth 패키지

이메일/소셜(Kakao·Google) 로그인, JWT 발급·갱신, 비밀번호 재설정을 담당하는 인증 패키지.

---

## API 엔드포인트

| Method | Path | 설명 | 인증 필요 |
|--------|------|------|-----------|
| POST | `/api/auth/signup` | 이메일 회원가입. `201` + accessToken 반환 | X |
| POST | `/api/auth/login` | 이메일 로그인. accessToken 반환 | X |
| POST | `/api/auth/refresh` | refreshToken 쿠키로 새 accessToken 발급 | X |
| POST | `/api/auth/logout` | Redis에서 refreshToken 삭제 + 쿠키 만료 | X |
| GET | `/api/auth/me` | 내 정보 조회 (id, email, nickname, provider) | O |
| GET | `/api/auth/oauth2/kakao` | 카카오 인증 URL로 302 redirect | X |
| GET | `/api/auth/oauth2/kakao/callback` | 카카오 code 처리, 쿠키 설정 후 `/` redirect | X |
| GET | `/api/auth/oauth2/google` | 구글 인증 URL로 302 redirect | X |
| GET | `/api/auth/oauth2/google/callback` | 구글 code 처리, 쿠키 설정 후 `/` redirect | X |
| POST | `/api/auth/password-reset/request` | 이메일로 6자리 인증 코드 발송 | X |
| POST | `/api/auth/password-reset/confirm` | 인증 코드 검증 후 비밀번호 변경, accessToken 반환 | X |

---

## 인증 흐름

### JWT 발급·검증

- `JwtProvider`가 HMAC-SHA 키(`jwt.secret`)로 accessToken / refreshToken을 서명한다.
- accessToken 유효기간: `jwt.access-token-expiry` (ms), refreshToken: `jwt.refresh-token-expiry` (ms).
- `JwtProvider.parse(token)` 은 검증 실패 시 예외 없이 `null` 반환.

### 요청별 JWT 처리

```
요청 → JwtFilter (OncePerRequestFilter)
  Authorization: Bearer {token} 헤더 파싱
  → JwtProvider.parse() 로 userId(UUID) 추출
  → 성공 시 request attribute "authenticatedUserId" 에 저장
  → 실패해도 filter chain 계속 진행 (401 차단은 컨트롤러/resolver 책임)
```

### refreshToken 쿠키

- 발급 시 `HttpOnly; Path=/api/auth; MaxAge=604800(7일)` 쿠키로 내려보낸다.
- refreshToken 자체는 Redis에 `refresh:{userId}` 키로 저장된다.
- `POST /api/auth/refresh` 는 쿠키 값과 Redis 저장 값을 대조하여 일치할 때만 새 accessToken 반환.
- 로그아웃 시 Redis 키 삭제 + maxAge=0 쿠키로 클라이언트 쿠키 만료.

### OAuth 흐름 (Kakao · Google)

```
GET /oauth2/{provider}
  → OAuthClient.authorizationUrl() 로 302 redirect

GET /oauth2/{provider}/callback?code=...
  → OAuthClient.fetchAccessToken(code)
  → OAuthClient.fetchUserInfo(accessToken)
  → email 로 User 조회, 없으면 신규 저장 (upsert)
  → TokenPair 발급
  → refreshToken: HttpOnly 쿠키
  → accessToken: HttpOnly 쿠키(path=/, maxAge=3600)
  → response.sendRedirect("/")
```

- 카카오 이메일 미제공 계정: `{id}@kakao.local` 을 email로 사용.
- 구글 이메일 미제공 계정: `{sub}@google.local` 을 email로 사용.

### OAuth 클라이언트 구현

`KakaoOAuthClient`, `GoogleOAuthClient` 모두 `RestClient` + `JdkClientHttpRequestFactory` 기반 (connect 5s / read 10s).

응답은 private data class로 타입 안전하게 역직렬화한다.
- Kakao: `access_token`, `kakao_account` 등 snake_case 필드는 `@JsonProperty` 사용
- Google: `access_token`만 `@JsonProperty` 필요, userinfo 필드(`sub`, `email`, `name`)는 camelCase와 동일

---

## 비밀번호 재설정 흐름

```
POST /api/auth/password-reset/request  { email }
  → 사용자 미존재 시 아무 응답 없이 return (이메일 열거 공격 방지)
  → 6자리 난수 생성 → Redis "password-reset:{email}" 에 TTL 15분 저장
  → JavaMailSender 로 이메일 발송 (mailSender null 이면 스킵)

POST /api/auth/password-reset/confirm  { email, token, newPassword }
  → 소셜 계정이면 403 SOCIAL_ACCOUNT
  → Redis 코드 대조 → 불일치·만료 시 400 INVALID_RESET_TOKEN
  → BCrypt 해시 후 user.changePassword() 호출
  → Redis 키 삭제
  → 새 accessToken 반환 (자동 로그인)
```

---

## @CurrentUser 동작 방식

`@CurrentUser` 는 컨트롤러 파라미터에 붙이는 어노테이션이다. `CurrentUserArgumentResolver` 가 처리한다.

```
supportsParameter: 파라미터 타입이 User && @CurrentUser 어노테이션 존재
resolveArgument:
  1. request attribute "authenticatedUserId" (UUID) 읽기
  2. null 이면 401 UNAUTHORIZED
  3. UserRepository.findByIdOrNull(userId) 로 User 조회
  4. null 이면 401 UNAUTHORIZED
  5. User 객체 반환
```

사용 예시:

```kotlin
@GetMapping("/me")
fun me(@CurrentUser user: User): UserResponse { ... }
```

JwtFilter 가 먼저 토큰을 검증해 attribute 에 저장하므로, `@CurrentUser` 파라미터가 있는 엔드포인트는 유효한 accessToken 없이 호출하면 자동으로 401 반환된다.
