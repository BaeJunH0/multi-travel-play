# user 패키지

소셜/로컬 인증을 모두 지원하는 사용자 도메인 모델과 JPA 리포지토리를 정의한다.

## User 엔티티 (`users` 테이블)

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | `UUID` | PK, 자동 생성 |
| `email` | `String` | 유니크, 로그인 식별자 |
| `nickname` | `String` | 표시 이름, 변경 가능 |
| `provider` | `AuthProvider` | 가입 경로 (아래 참고) |
| `password` | `String?` | 로컬 로그인 전용, nullable (소셜 로그인 시 null) |
| `createdAt` | `LocalDateTime` | 가입 시각, 변경 불가 |

`changePassword(encoded: String)` — 비밀번호를 교체할 때 사용하는 유일한 메서드. 반드시 인코딩된 값을 전달해야 한다.

## AuthProvider enum

| 값 | 설명 |
|----|------|
| `LOCAL` | 이메일/비밀번호 자체 가입 |
| `KAKAO` | 카카오 OAuth |
| `GOOGLE` | 구글 OAuth |

소셜 로그인 사용자는 `password`가 null이므로, 비밀번호 관련 로직 작성 시 `provider == LOCAL` 여부를 먼저 확인할 것.

## UserRepository

`JpaRepository<User, UUID>` 를 상속하며 추가 메서드는 두 개다.

| 메서드 | 반환 타입 | 용도 |
|--------|-----------|------|
| `findByEmail(email)` | `User?` | 이메일로 사용자 조회 (로그인, 중복 확인) |
| `existsByEmail(email)` | `Boolean` | 이메일 존재 여부만 확인 (회원가입 중복 검사) |
