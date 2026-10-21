# spec-test.md — 테스트 전략 및 명세

> 작성 기준일: 2026-10-14

---

## 1. 테스트 범위

3개 계층을 다룬다.

| 계층 | 방식 | 목적 |
|------|------|------|
| Service (단위) | MockK + Kotest BehaviorSpec, Spring Context 없음 | 비즈니스 규칙, 권한 체크, 예외 조건 |
| Repository (통합) | `@DataJpaTest` + H2 in-memory + `SpringExtension` | 쿼리 메서드 정확성, 정렬, 필터링 |
| Domain/Entity (단위) | 순수 Kotest BehaviorSpec, 의존성 없음 | 엔티티 메서드, DTO 팩토리, 예외 구조 |

프론트(`frontend/`) 테스트는 [8. 프론트 테스트](#8-프론트-테스트)에 따로 정리한다.

---

## 2. 의존성

```kotlin
// build.gradle.kts
testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
testImplementation("io.kotest:kotest-assertions-core:5.9.1")
testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
testImplementation("io.mockk:mockk:1.13.12")
testImplementation("com.tngtech.archunit:archunit:1.3.0")
testRuntimeOnly("com.h2database:h2")
```

| 라이브러리 | 역할 |
|-----------|------|
| `kotest-runner-junit5` | Kotest Spec을 JUnit 5 플랫폼 위에서 실행 |
| `kotest-assertions-core` | `shouldBe`, `shouldThrow`, `shouldHaveSize` 등 단언문 |
| `kotest-extensions-spring` | `@DataJpaTest`와 Kotest BehaviorSpec 연동 |
| `mockk` | Kotlin 친화적 Mock 라이브러리 |
| `archunit` | 패키지 · 계층 구조 규칙 검사 (`architecture/ArchitectureTest.kt`) |
| `h2` | Repository 테스트용 in-memory DB |

---

## 3. 테스트 형식

### 3-1. Spec 스타일: `BehaviorSpec`

```kotlin
class FooServiceTest : BehaviorSpec({
    given("메서드명 또는 상황 설명") {
        `when`("구체적인 입력 조건") {
            then("기대 결과") {
                // 단언문
            }
        }
    }
})
```

`given → when → then` 3단 계층으로 **무엇을**, **어떤 조건에서**, **어떻게 동작하는지**를 분리한다.  
`when`은 Kotlin 예약어이므로 백틱(`` ` ``)으로 감싼다.

### 3-2. Service 단위 테스트 — Mock 생성 및 격리

```kotlin
class FooServiceTest : BehaviorSpec({
    val repository = mockk<FooRepository>()
    val service = FooService(repository)          // 직접 생성, DI 컨테이너 없음

    beforeEach { clearAllMocks() }                // 테스트 간 상태 격리
})
```

- Mock 객체는 Spec 블록 최상단에 `val`로 선언한다.
- `beforeEach { clearAllMocks() }` 로 각 `then` 블록 실행 전 모든 stub·verify 기록을 초기화한다.

### 3-3. Repository 통합 테스트 — `@DataJpaTest` + `SpringExtension`

```kotlin
@DataJpaTest
class FooRepositoryTest(
    private val fooRepository: FooRepository,
) : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    init {
        given("findByX") { ... }
    }
}
```

- `@DataJpaTest`는 JPA 관련 빈만 로드하고 웹·서비스 계층은 제외한다.
- `SpringExtension`이 Spring 컨텍스트와 Kotest BehaviorSpec을 연결한다.
- H2 설정은 `backend/src/test/resources/application.yml`에 정의되어 있다.
- 테스트 간 격리는 고유한 이메일·suffix를 사용해 충돌을 방지한다. (`Transactional` 자동 롤백 없이 독립적 데이터 생성 방식 채택)

### 3-4. Domain 단위 테스트 — 순수 BehaviorSpec

```kotlin
class FooTest : BehaviorSpec({
    given("메서드 또는 팩토리") {
        `when`("조건") {
            then("결과") { ... }
        }
    }
})
```

Spring 컨텍스트, MockK 모두 불필요하다. 엔티티와 DTO 클래스를 직접 인스턴스화한다.

### 3-5. 픽스처 패턴

```kotlin
// 인라인 직접 생성
val user = User(email = "user@test.com", nickname = "유저", provider = AuthProvider.LOCAL)

// Repository 테스트용 헬퍼
private fun savedUser(suffix: String) = userRepository.save(User(email = "user$suffix@test.com", ...))

// 역할·상태 변형 헬퍼
fun memberWith(role: TripRole) = TripMember(trip = trip, user = user, role = role)
```

### 3-6. 단언문

```kotlin
// 값 비교
result.accessToken shouldBe accessToken

// 예외 타입 + 상태 코드
val ex = shouldThrow<ResponseStatusException> { service.method(...) }
ex.statusCode shouldBe HttpStatus.FORBIDDEN

// 커스텀 예외
val ex = shouldThrow<VersionConflictException> { service.updateBlock(...) }
ex.currentBlock.version shouldBe 1L

// 423 Locked (HttpStatus enum 미포함 코드)
ex.statusCode.value() shouldBe 423

// 컬렉션
result shouldHaveSize 3

// Mock 호출 검증
verify { repository.save(match { it.role == TripRole.OWNER }) }
verify(exactly = 0) { repository.delete(any()) }
```

---

## 4. H2 설정 (`backend/src/test/resources/application.yml`)

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
```

- `MODE=MySQL`: H2가 MySQL 구문을 허용한다.
- `NON_KEYWORDS=VALUE`: `value` 컬럼명(`@Version` 필드 등) 예약어 충돌 방지.
- `DB_CLOSE_DELAY=-1`: JVM 종료 전까지 메모리 DB를 유지한다.

---

## 5. 테스트 파일 목록

### Service 단위 테스트

| 파일 | 대상 서비스 | 시나리오 수 |
|------|------------|------------|
| `auth/AuthServiceTest.kt` | `AuthService` | 13 |
| `auth/PasswordResetServiceTest.kt` | `PasswordResetService` | 7 |
| `auth/PasswordResetCodeStoreTest.kt` | `PasswordResetCodeStore` (만료는 테스트용 Clock으로 검증) | 5 |
| `trip/TripServiceTest.kt` | `TripService` | 14 |
| `block/BlockServiceTest.kt` | `BlockService` (잠금 만료는 테스트용 Clock으로 검증) | 33 |
| `budget/BudgetServiceTest.kt` | `BudgetService` | 10 |
| `ai/AiServiceTest.kt` | `AiService.apply` | 3 |
| `ai/AnthropicClientTest.kt` | `AnthropicClient.parse` (Messages API 응답 파싱), `toAiException` (API 오류 매핑) | 9 |
| `block/BlockControllerTest.kt` | `BlockController` 충돌 브로드캐스트 | 3 |
| `sse/PresenceStoreTest.kt` | `PresenceStore`, `PresenceUser.avatarColorOf` | 5 |
| `sse/TripConnectionRegistryTest.kt` | `TripConnectionRegistry`, `TripConnection.send` | 6 |
| `sse/TripEventServiceTest.kt` | `TripEventService` (연결 · presence · 연결 종료 시 잠금 해제 · heartbeat) | 9 |
| `invite/InviteServiceTest.kt` | `InviteService` | 9 |
| `member/MemberServiceTest.kt` | `MemberService` | 13 |

### Repository 통합 테스트

| 파일 | 대상 | 시나리오 수 |
|------|------|------------|
| `user/UserRepositoryTest.kt` | `UserRepository` | 5 |
| `trip/TripRepositoryTest.kt` | `TripRepository`, `TripMemberRepository` | 9 |
| `block/BlockRepositoryTest.kt` | `BlockRepository` | 8 |
| `budget/BudgetRepositoryTest.kt` | `BudgetRepository` | 4 |
| `auth/RefreshTokenPersistenceTest.kt` | `RefreshTokenRepository`, `AuthService.login` → `refresh` | 2 |

### Domain/Entity 단위 테스트

| 파일 | 대상 | 시나리오 수 |
|------|------|------------|
| `user/UserTest.kt` | `User.changePassword()` | 3 |
| `trip/TripResultTest.kt` | `TripResult.Detail.of()` | 3 |
| `block/BlockResultTest.kt` | `BlockResult.of()` | 5 |
| `common/ExceptionTest.kt` | `BusinessException` | 3 |
| `block/VersionConflictExceptionTest.kt` | `VersionConflictException`, `BlockExceptionHandler` (409 응답 형태) | 3 |
| `common/GlobalExceptionHandlerTest.kt` | `GlobalExceptionHandler.handleMessageNotReadable` | 1 |
| `budget/BlockBudgetSyncListenerTest.kt` | `BlockBudgetSyncListener` | 4 |

### 기타 통합 테스트

| 파일 | 대상 | 방식 |
|------|------|------|
| `block/BlockLockConcurrencyTest.kt` | heartbeat 잠금 연장 · 해제와 동시 요청이 겹칠 때 | `@SpringBootTest` + `TransactionTemplate`(REQUIRES_NEW)로 두 트랜잭션을 끼워 넣음 |
| `sse/TripEventControllerTest.kt` | `GET /api/trips/{tripId}/events` 인증 · 권한 · 첫 `PRESENCE` | `@SpringBootTest(RANDOM_PORT)` + 실제 HTTP 요청 |
| `config/WebConfigCorsTest.kt` | `WebConfig`의 REST `/api/**` CORS | `@SpringBootTest(RANDOM_PORT)` + 실제 preflight 요청 |
| `architecture/ArchitectureTest.kt` | 패키지 · 계층 의존 · 클래스 위치/이름 규칙 (`convention-dto.md`) | ArchUnit + `BehaviorSpec` (Spring 컨텍스트 없음) |
| `TravelApplicationTests.kt` | 애플리케이션 컨텍스트 로딩 (`contextLoads`) | `@SpringBootTest` + JUnit `@Test` |

---

## 6. 시나리오 명세

### 6-1. AuthService

| given | when | then |
|-------|------|------|
| signup | 이메일 중복 없음 | 유저 저장, refreshToken 저장, TokenPair 반환 |
| signup | 이메일 중복 | 409 CONFLICT |
| login | 이메일·비밀번호 정상 | refreshToken 저장, TokenPair 반환 |
| login | 이메일 미존재 | 401 UNAUTHORIZED |
| login | 비밀번호 불일치 | 401 UNAUTHORIZED |
| login | 소셜 계정 (password=null) | 401 UNAUTHORIZED |
| refresh | 저장된 토큰과 일치 | 새 accessToken 반환 |
| refresh | 토큰 파싱 실패 | 401 UNAUTHORIZED |
| refresh | 저장된 토큰과 불일치 | 401 UNAUTHORIZED |
| logout | 유효한 토큰 | 저장된 토큰 삭제 |
| logout | 파싱 불가 토큰 | 삭제 없이 종료 |
| kakaoLogin | 신규 사용자 | 유저 저장 후 TokenPair |
| kakaoLogin | 기존 사용자 | 저장 없이 TokenPair |

### 6-1-1. PasswordResetService

| given | when | then |
|-------|------|------|
| requestReset | LOCAL 계정 | 코드 저장, 같은 코드를 메일로 발송 |
| requestReset | 미가입 이메일 | 저장·발송 없이 종료 |
| requestReset | 소셜 계정 | 저장·발송 없이 종료 |
| confirmReset | 코드 일치 + LOCAL 계정 | 비밀번호 변경, 코드 삭제, accessToken 반환 |
| confirmReset | 코드 불일치 | 400 `INVALID_RESET_TOKEN` |
| confirmReset | 미가입 이메일 | 400 `INVALID_RESET_TOKEN` |
| confirmReset | 소셜 계정 | 400 `INVALID_RESET_TOKEN`, 비밀번호 유지 |

### 6-1-2. PasswordResetCodeStore

| given | when | then |
|-------|------|------|
| find | 저장 후 15분 전 | 저장된 코드 반환 |
| find | 저장 후 15분 경과 | null |
| find | 같은 이메일로 재저장 | 마지막 코드만 유효 |
| find | 저장한 적 없는 이메일 | null |
| remove | 저장된 코드 삭제 | 이후 find는 null |

### 6-1-3. RefreshTokenPersistenceTest (`@SpringBootTest`)

| given | when | then |
|-------|------|------|
| refreshTokenRepository.save | 같은 유저로 재저장 | 행을 새로 만들지 않고 토큰 덮어씀 |
| authService.login | 이메일 로그인 성공 | 발급한 refreshToken으로 refresh 가능 |

### 6-2. TripService

| given | when | then |
|-------|------|------|
| create | 정상 요청 | Trip 저장, OWNER TripMember 등록 |
| getList | 여행 2개 보유 | 2개 목록 반환 |
| getList | 여행 없음 | 빈 리스트 반환 |
| getDetail | 멤버인 경우 | 상세 반환 (days 포함) |
| getDetail | 멤버 아님 | 403 FORBIDDEN |
| getDetail | 여행 미존재 | 404 NOT_FOUND |
| update | EDITOR | 필드 수정 반영 |
| update | OWNER | 필드 수정 반영 |
| update | VIEWER | 403 FORBIDDEN |
| update | 멤버 아님 | 403 FORBIDDEN |
| delete | OWNER | 삭제 실행 |
| delete | EDITOR | 403 FORBIDDEN |
| delete | VIEWER | 403 FORBIDDEN |
| delete | 비멤버 | 403 FORBIDDEN |

### 6-3. BlockService

| given | when | then |
|-------|------|------|
| getBlocks | 멤버 | 블록 목록 반환 |
| getBlocks | 비멤버 | 403 FORBIDDEN |
| addBlock | EDITOR, 기존 블록 있음 | `lastPosition + 1.0`으로 저장 |
| addBlock | EDITOR, 첫 번째 블록 | `position = 1.0`으로 저장 |
| addBlock | VIEWER | 403 FORBIDDEN |
| addBlock | block에 쓰지 않는 카테고리(FLIGHT) | 400 BAD_REQUEST, 저장 안 함 |
| updateBlock | 잠금 없음 + version 일치 | 필드 수정 반영, cost 미포함이라 이벤트 미발행 |
| updateBlock | cost 변경 | `BlockCostChangedEvent` 발행 |
| updateBlock | version 불일치 | `VersionConflictException` (현재 블록 포함) |
| updateBlock | 타인이 잠금 | 423 LOCKED |
| updateBlock | 타인의 잠금이 만료됨 | 잠금 없는 것으로 보고 정상 수정 |
| updateBlock | 본인이 잠금 | 정상 수정 |
| updateBlock | 다른 trip의 blockId | 404 NOT_FOUND |
| moveBlock | version 일치 | dayNumber·position 변경 |
| moveBlock | version 불일치 | `VersionConflictException` |
| moveBlock | 타인이 잠금 | 423 LOCKED, 위치 무변경 |
| moveBlock | 본인이 잠금 | 정상 이동 |
| deleteBlock | EDITOR | 삭제 실행 |
| deleteBlock | VIEWER | 403 FORBIDDEN |
| deleteBlock | 타인이 잠금 | 423 LOCKED, 삭제·이벤트 없음 |
| deleteBlock | 본인이 잠금 | 삭제 실행 |
| lockBlock | 잠금 없음 | lockedBy = 본인, lockExpiresAt = 지금 + 3분 |
| lockBlock | 타인의 잠금이 만료됨 | lockedBy = 본인, lockExpiresAt = 지금 + 3분 |
| lockBlock | 본인이 이미 잠금 | 정상 반환 |
| lockBlock | 타인이 잠금 | 423 LOCKED |
| unlockBlock | 본인 잠금 | lockedBy · lockExpiresAt = null |
| unlockBlock | 타인 잠금 | lockedBy 유지 (무변경) |
| releaseLocks | 유저가 trip에서 잠금 2개 보유, 그중 1개는 그 사이 바뀜 | 블록마다 `releaseLock`, 실제 해제된(1행) blockId만 반환 |
| refreshLocks | 잠금자의 연결이 살아 있음 (만료 지남) | `extendLock`으로 만료를 지금 + 3분으로 연장 |
| refreshLocks | 잠금자 연결 없음 + 만료 지남 | `releaseExpiredLock`, (tripId, blockId) 반환 |
| refreshLocks | 만료 지남, 그 사이 다른 사용자가 잠금 (0행) | 해제 목록에 넣지 않음 |
| refreshLocks | 잠금자 연결 없음 + 만료 전 | UPDATE 호출 없음 |
| reorderBlocks | Day별 복수 블록 | Day 내 1.0, 2.0, 3.0 ... 재정규화 |

### 6-3-1. AiService.apply

| given | when | then |
|-------|------|------|
| apply | block용 카테고리만 선택 | 모두 저장, tempId echo, lat/lng 보강 |
| apply | block에 쓰지 않는 카테고리(ETC) 포함 | 400 BAD_REQUEST, 아무것도 저장 안 함 |
| apply | VIEWER | 403 FORBIDDEN |

### 6-3-2. AnthropicClient.parse / toAiException

| given | when | then |
|-------|------|------|
| parse | thinking 블록 다음에 text 블록 | text 블록의 JSON 파싱 |
| parse | text 블록만 있음 | text 블록의 JSON 파싱 |
| parse | text가 마크다운 코드블록으로 감싸짐 | 코드블록 표시를 벗겨내고 파싱 |
| parse | `stop_reason`이 `max_tokens` | 502 `AI_RESPONSE_INVALID` |
| parse | text 블록 없음 (thinking만) | 502 `AI_RESPONSE_INVALID` |
| parse | text가 JSON이 아님 | 502 `AI_RESPONSE_INVALID` |
| toAiException | 429·500·529 응답 | 503 `AI_UNAVAILABLE` |
| toAiException | 400·401·404 응답 | 502 `AI_REQUEST_FAILED` |
| toAiException | 시간 초과 (`ResourceAccessException`) | 503 `AI_UNAVAILABLE` |

### 6-3-3. BlockController

| given | when | then |
|-------|------|------|
| updateBlock | version 충돌 | `CONFLICT` 브로드캐스트, `VersionConflictException` 재전파, `UPDATE` 미발행 |
| updateBlock | 정상 수정 | `UPDATE`만 브로드캐스트 |
| moveBlock | version 충돌 | `CONFLICT` 브로드캐스트, `VersionConflictException` 재전파, `MOVE` 미발행 |

### 6-3-4. PresenceStore

| given | when | then |
|-------|------|------|
| join | 이미 접속 중인 유저가 다른 탭으로 접속 | 기존 정보(activeDay) 유지 |
| updateActiveDay | 접속 중인 유저 | activeDay 변경, true |
| updateActiveDay | 접속 중이 아닌 유저 | 추가하지 않음, false |
| leave | 같은 trip의 한 유저 | 그 유저만 제거 |
| avatarColorOf | userId | 예전 프론트 계산과 같은 색 (해시가 음수인 경우 포함) |

### 6-3-5. TripConnectionRegistry

| given | when | then |
|-------|------|------|
| remove | 유저의 유일한 연결 | true (마지막 연결) |
| remove | 같은 유저의 다른 탭 연결이 남음 | false, 남은 연결까지 제거하면 true |
| remove | 같은 유저의 다른 trip 연결만 남음 | true |
| remove | 이미 제거된 연결 | false (종료 콜백 중복 처리 방지) |
| of | 여러 trip 연결이 섞여 있음 | 해당 trip 연결만 반환 |
| TripConnection.send | 전송 실패 | `completeWithError`, false |

### 6-3-6. TripEventService

| given | when | then |
|-------|------|------|
| connect | 멤버 | 연결 등록, presence 추가, `PRESENCE` 브로드캐스트 |
| connect | 비멤버 | 403, 등록 안 함 |
| updatePresence | 접속 중인 유저 | activeDay 변경, `PRESENCE` 브로드캐스트 |
| updatePresence | 접속 중이 아닌 유저 | 발행 없음 |
| disconnect | 유저의 마지막 연결 | presence 제거 + `PRESENCE`, 잠금 해제 + 블록마다 `UNLOCK` |
| disconnect | 같은 유저의 다른 탭 연결이 남음 | presence · 잠금 유지, 발행 없음 |
| disconnect | 같은 연결 종료 콜백 2번 | 한 번만 처리 |
| heartbeat | 전송 실패한 연결 있음 | 끊긴 연결로 처리(잠금 해제 + `UNLOCK`), 살아 있는 연결만 `refreshLocks`에 전달 |
| heartbeat | 만료로 해제된 잠금 있음 | 해당 trip에 `UNLOCK` |

### 6-4. BudgetService

| given | when | then |
|-------|------|------|
| getItems | VIEWER 이상 멤버 | 목록 반환 |
| getItems | 비멤버 | 403 FORBIDDEN |
| create | EDITOR, 여행 존재 | 항목 저장 반환 |
| create | VIEWER | 403 FORBIDDEN |
| create | 여행 미존재 | 404 NOT_FOUND |
| update | EDITOR, 해당 trip 항목 | 필드 수정 반영 |
| update | 다른 trip 항목 | 404 NOT_FOUND |
| update | VIEWER | 403 FORBIDDEN |
| delete | EDITOR, 항목 존재 | 삭제 실행 |
| delete | 항목 미존재 | 404 NOT_FOUND |

### 6-5. InviteService

| given | when | then |
|-------|------|------|
| createInviteLink | EDITOR, 토큰 없음 | 16자리 토큰 신규 생성 |
| createInviteLink | EDITOR, 토큰 이미 존재 | 기존 토큰 재사용 |
| createInviteLink | VIEWER | 403 FORBIDDEN |
| createInviteLink | 비멤버 | 403 FORBIDDEN |
| getInviteInfo | 유효한 토큰 | 여행 미리보기 반환 |
| getInviteInfo | 미존재 토큰 | 404 NOT_FOUND |
| accept | 신규 사용자 | VIEWER TripMember 저장, tripId 반환 |
| accept | 이미 멤버 | 저장 없이 tripId 반환 (멱등) |
| accept | 미존재 토큰 | 404 NOT_FOUND |

### 6-6. MemberService

| given | when | then |
|-------|------|------|
| getMembers | 멤버 | 전체 목록 반환 |
| getMembers | 비멤버 | 403 FORBIDDEN |
| updateRole | OWNER → 대상 EDITOR | 역할 변경 |
| updateRole | OWNER → 대상 VIEWER 승격 | 역할 변경 |
| updateRole | 요청자 EDITOR | 403 FORBIDDEN |
| updateRole | 대상이 OWNER | 403 FORBIDDEN |
| updateRole | newRole = OWNER | 400 BAD_REQUEST |
| updateRole | 요청자 비멤버 | 403 FORBIDDEN |
| removeMember | OWNER → EDITOR 제거 | 삭제 실행 |
| removeMember | OWNER → VIEWER 제거 | 삭제 실행 |
| removeMember | OWNER 제거 시도 | 403 FORBIDDEN |
| removeMember | 요청자 EDITOR | 403 FORBIDDEN |
| removeMember | 대상 미존재 | 404 NOT_FOUND |

### 6-7. UserRepository

| given | when | then |
|-------|------|------|
| findByEmail | 이메일 존재 | User 반환, 필드 정확 |
| findByEmail | 이메일 미존재 | null 반환 |
| findByEmail | 소셜 로그인 사용자 | provider·password(null) 함께 반환 |
| existsByEmail | 이메일 존재 | true |
| existsByEmail | 이메일 미존재 | false |

### 6-8. TripRepository / TripMemberRepository

| given | when | then |
|-------|------|------|
| findByShareToken | 토큰 존재 | Trip 반환 |
| findByShareToken | 토큰 미존재 | null 반환 |
| findByTripIdAndUserId | 멤버 존재 | TripMember 반환 |
| findByTripIdAndUserId | 멤버 미존재 | null 반환 |
| findByTripIdAndRole | 해당 role 존재 | TripMember 반환 |
| findByTripIdAndRole | 해당 role 미존재 | null 반환 |
| countByTripId | 멤버 3명 | 3 반환 |
| findAllByTripId | 멤버 2명 | 리스트 2개 반환 |
| findAllWithTripByUserId | 2개 여행 소속 | Trip FETCH JOIN된 TripMember 2개 |

### 6-9. BlockRepository

| given | when | then |
|-------|------|------|
| findAllByTripIdOrderByDayNumberAscPositionAsc | 여러 day 블록 | dayNumber→position 오름차순 정렬 |
| findAllByTripIdOrderByDayNumberAscPositionAsc | 블록 없음 | 빈 리스트 |
| findAllByTripIdOrderByDayNumberAscPositionAsc | 다른 여행 블록 혼재 | 해당 여행 블록만 |
| findTopByTripIdAndDayNumberOrderByPositionDesc | 해당 day 블록 여러 개 | position 최대값 블록 |
| findTopByTripIdAndDayNumberOrderByPositionDesc | 블록 없음 | null 반환 |
| findTopByTripIdAndDayNumberOrderByPositionDesc | 다른 day 블록 혼재 | 지정 day만 대상 |
| lockedBy 변경 | lockedBy만 변경 후 저장 | version 증가하지 않음 |
| lockedBy 변경 | lockExpiresAt만 연장 후 저장 | version 증가하지 않음 |

### 6-10. BudgetRepository

| given | when | then |
|-------|------|------|
| findAllByTripId | 항목 3개 | 3개 반환 |
| findAllByTripId | 항목 없음 | 빈 리스트 |
| findAllByTripId | 다른 여행 항목 혼재 | 해당 여행 항목만 |
| findAllByTripId | category·amount 검증 | 저장값 정확 반영 |

### 6-11. User.changePassword()

| given | when | then |
|-------|------|------|
| LOCAL 유저 | 인코딩된 비밀번호 전달 | password 필드 교체 |
| 소셜 유저 (password=null) | changePassword 호출 | password 새로 설정 |
| 기존 비밀번호 있는 유저 | 재호출 | 이전 값 덮어씀 |

### 6-12. TripResult.of()

| given | when | then |
|-------|------|------|
| of() | 3박4일 여행 | days 4개, dayNumber·date 정확 |
| of() | 당일치기 (start==end) | days 1개 |
| of() | 필드 매핑 전체 확인 | id·title·role·memberCount 정확 반영 |

### 6-13. BlockResult.of()

| given | when | then |
|-------|------|------|
| of() | 잠금 없음 | lockedBy·lockedByNickname null |
| of() | 타인이 잠금 | lockedBy UUID, lockedByNickname 닉네임 |
| of() | startTime 있음 | "HH:mm" 문자열 변환 |
| of() | startTime 없음 | null |
| of() | 전체 필드 매핑 | id·dayNumber·position·cost·memo·version 정확 |

### 6-14. BusinessException / VersionConflictException

| given | when | then |
|-------|------|------|
| BusinessException | code·message만 전달 | status = 400 BAD_REQUEST |
| BusinessException | status 명시 | 지정 HttpStatus |
| BusinessException | 404 NOT_FOUND | status = NOT_FOUND |
| VersionConflictException | currentBlock 전달 | 메시지 고정, currentBlock 포함 |
| VersionConflictException | 타입 확인 | RuntimeException 상속 |
| BlockExceptionHandler | VersionConflictException 처리 | 409 + `code=VERSION_CONFLICT`, `message`, `currentBlock` |

### 6-15. GlobalExceptionHandlerTest

| given | when | then |
|-------|------|------|
| HttpMessageNotReadableException | 요청 바디 파싱 실패 | 400 + `ErrorResponse("INVALID_REQUEST", "요청 본문이 올바르지 않습니다.")` |

### 6-16. BlockBudgetSyncListenerTest

| given | when | then |
|-------|------|------|
| onCostChanged | 연동된 BudgetItem 없음 | blockId로 연결된 새 BudgetItem 생성 |
| onCostChanged | 연동된 BudgetItem 이미 있음 | category/amount/memo 갱신 (save 재호출 없음) |
| onBlockDeleted | 연동된 BudgetItem 있음 | 해당 BudgetItem 삭제 |
| onBlockDeleted | 연동된 BudgetItem 없음 | 아무 일도 일어나지 않음 |

### 6-17. TripEventControllerTest (`@SpringBootTest(RANDOM_PORT)`)

| given | when | then |
|-------|------|------|
| `GET /api/trips/{tripId}/events` | 멤버 | 200 `text/event-stream`, 첫 이벤트로 자신이 포함된 `PRESENCE` |
| `GET /api/trips/{tripId}/events` | 비멤버 | 403 |
| `GET /api/trips/{tripId}/events` | 토큰 없음 | 401 |

### 6-17-1. BlockLockConcurrencyTest (`@SpringBootTest`)

바깥 트랜잭션이 잠긴 블록을 먼저 읽은 뒤, 별도 트랜잭션(REQUIRES_NEW)이 같은 블록을 바꿔 커밋하고, 바깥 트랜잭션에서 잠금 연장 · 해제를 실행한다.

| given | when | then |
|-------|------|------|
| refreshLocks (연장) | 읽은 뒤 잠금자가 placeName 수정 | 수정 유지, 만료만 연장 (예외 없음) |
| refreshLocks (만료 해제) | 읽은 뒤 다른 사용자가 만료된 잠금을 새로 잡음 | 새 잠금 유지, 해제 목록에 없음 |
| releaseLocks (연결 종료) | 읽은 뒤 placeName 수정 | 수정 유지, 잠금 해제 |

### 6-18. WebConfigCorsTest

| given | when | then |
|-------|------|------|
| `OPTIONS /api/trips` preflight | 허용된 origin(`cors.allowed-origins`) | 200, `Access-Control-Allow-Origin`=해당 origin, `Access-Control-Allow-Credentials: true` |
| `OPTIONS /api/trips` preflight | 허용되지 않은 origin | 403 |

---

## 7. 주요 결정 사항

### Spring Context 미사용 (Service·Domain 계층)
`@SpringBootTest` 없이 Service를 직접 생성자로 인스턴스화한다. DB 연결이 불필요하므로 테스트 실행 속도가 빠르고, CI 환경에서 인프라 의존성이 없다.

### `@DataJpaTest` + H2 (Repository 계층)
전체 애플리케이션 컨텍스트 없이 JPA 빈만 로드한다. H2 `MODE=MySQL`로 MySQL 구문을 허용하면서도 외부 인프라 없이 쿼리 메서드를 검증한다.

### Repository 테스트 격리 전략
`@Transactional` 자동 롤백 대신 각 `then` 블록에서 고유한 이메일·suffix를 가진 새 엔티티를 생성한다. Kotest BehaviorSpec은 JUnit의 트랜잭션 롤백과 통합하기 복잡하므로 데이터 독립성으로 대신한다.

### MockK 선택 이유
Mockito는 Kotlin의 `final class` (data class 포함)를 기본 Mock할 수 없어 별도 설정이 필요하다. MockK는 Kotlin 친화적으로 설계되어 data class, object, extension function, companion object를 추가 설정 없이 Mock한다.

### `VersionConflictException` 타입 검증
`ResponseStatusException`이 아닌 `VersionConflictException`을 직접 `shouldThrow`로 잡아 `currentBlock` 필드를 검증한다. 이 예외는 `block/presentation/BlockExceptionHandler`에서 409로 변환되며, 응답 body에 `currentBlock`을 포함하는 것이 핵심 계약이기 때문이다.

### 423 상태 코드 검증 방식
`HttpStatus` enum에 423(`LOCKED`)이 존재하지만, 서비스 코드가 `HttpStatus.valueOf(423)`로 생성하므로 테스트에서도 `.statusCode.value() shouldBe 423`으로 숫자 값을 직접 비교한다.

### 잠금 만료 시각 검증
`BlockService`는 `Clock`을 생성자로 받는다 (기본값 `Clock.systemDefaultZone()`). 테스트는 `Clock.fixed`를 넘겨 "지금"을 고정하고, 만료 시각을 지금 기준 앞뒤로 둔 블록으로 만료 판단 · 연장(지금 + 3분)을 정확한 값으로 비교한다. `makeBlock(lockedBy = ...)`은 기본으로 아직 만료되지 않은 잠금(지금 + 1분)을 만든다.

### `unlockBlock` 상태 직접 검증
`unlockBlock`은 반환값이 없다. 실행 후 블록 엔티티의 `lockedBy` 필드를 직접 읽어 상태 변이를 확인한다. `verify`로 Repository 호출을 검증하는 대신 엔티티 상태를 보는 것이 의도를 더 명확히 표현한다.

### `reorderBlocks` 픽스처 전략
Day별로 비정상적인 소수 position(`0.1`, `0.15`, `0.2`)을 가진 블록을 만들어, 서비스 실행 후 정수에 가까운 값(`1.0`, `2.0`, `3.0`)으로 재정규화되는지를 엔티티 필드 직접 비교로 확인한다.

---

## 8. 프론트 테스트

### 8-1. 도구와 실행

| 라이브러리 | 역할 |
|-----------|------|
| `vitest` | 테스트 러너·단언문. `vite.config.ts`의 `test` 항목으로 Vite 설정을 그대로 쓴다 |
| `jsdom` | 브라우저 DOM 환경 (`test.environment: 'jsdom'`) |
| `@testing-library/react` | 훅을 컴포넌트 없이 렌더링하는 `renderHook`, 상태 갱신을 감싸는 `act` |

```bash
cd frontend
npm test          # vitest run — 한 번 돌고 끝난다 (CI와 같다)
npx vitest        # 파일을 고칠 때마다 다시 도는 watch 모드
```

결과는 터미널과 `frontend/test-results/junit.xml`(JUnit XML)에 남는다. `test-results/`는 git에서 제외한다.

### 8-2. 파일 위치와 형식

- 테스트 파일은 대상 파일 옆에 `<대상>.test.ts`로 둔다. 예) `src/hooks/useTripBlocks.ts` → `src/hooks/useTripBlocks.test.ts`
- `describe`(대상) → `it`(조건과 기대 결과)로 쓴다. `describe`·`it`·`expect`는 `vitest`에서 직접 import한다(전역 주입을 쓰지 않는다).
- 훅은 `renderHook(() => useHook(...))`으로 렌더링하고, 상태를 바꾸는 호출은 `act(() => ...)`로 감싼 뒤 `result.current`를 확인한다.
- 픽스처는 기본값을 채운 팩토리에 바꿀 필드만 넘긴다. 예) `block('a', { position: 2 })`

```ts
describe('useTripBlocks.handleTripEvent', () => {
  it('#82-TC-3 MOVE는 dayNumber·position·version만 바꾼다', () => {
    const { result } = renderHook(() => useTripBlocks('trip-1', [block('a')]))
    act(() => result.current.handleTripEvent({ action: 'MOVE', tripId: 'trip-1', payload: { /* ... */ } }))
    expect(result.current.blocks[0]).toMatchObject({ dayNumber: 2 })
  })
})
```

### 8-3. 테스트 케이스 태그

이슈의 테스트 케이스와 연결되는 테스트는 `it` 문구 맨 앞에 `#<이슈번호>-TC-<n>`을 넣는다(백엔드 `then(...)` 문구와 같은 규칙). JUnit XML의 testcase 이름에 그대로 남아, 이 태그로 해당 케이스의 통과 여부를 찾는다.

### 8-4. 테스트 파일 목록

| 파일 | 대상 | 시나리오 수 |
|------|------|------------|
| `src/hooks/useTripBlocks.test.ts` | `lockedMessage`, `useTripBlocks.handleTripEvent` (SSE 이벤트 반영) | 5 |
