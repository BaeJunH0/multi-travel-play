# travel_1 — 주요 기술 스택 선정 사유 및 의사결정

> 이 문서는 프로젝트 초기 기술 선택의 배경과 판단 근거를 기록한다.
> 코드베이스 분석 기준일: 2026-06-13

---

## 1. 언어: Kotlin 2.2.21

**선택 이유**

- **Null-safety**: `User.password: String?` 처럼 nullable 여부를 타입 시스템에서 강제한다. 소셜 로그인 사용자는 비밀번호가 없으므로 nullable 필드가 필수인데, Java였다면 런타임 NPE 위험이 상존한다.
- **data class**: DTO(`SignupRequest`, `BlockResponse` 등)를 보일러플레이트 없이 선언할 수 있다. equals/hashCode/toString/copy가 자동 생성된다.
- **확장 함수**: `String.toErrorCode()` 같은 패턴으로 기존 클래스를 건드리지 않고 도메인 표현력을 높인다.
- **간결한 람다·let·also**: `.also { eventPublisher.publish(...) }` 패턴으로 사이드이펙트를 체인 끝에 붙여 흐름을 명확히 표현한다.

**트레이드오프**

- 컴파일 속도가 Java보다 느리다. Kotlin 2.x의 K2 컴파일러로 일부 개선됐지만 대형 프로젝트에서는 여전히 체감된다.
- Java 생태계 라이브러리 일부가 Kotlin DSL을 완전히 지원하지 않아 `@field:` prefix 같은 어노테이션 우회가 필요하다 (`build.gradle.kts`의 `-Xannotation-default-target=param-property`).

---

## 2. 프레임워크: Spring Boot 4.0.6

**선택 이유**

- Spring Boot 4.x는 Spring Framework 7 기반으로, **가상 스레드(Virtual Thread)** 지원이 안정화되어 있다. 현재는 동기 블로킹 모델(`RestClient` + `JdkClientHttpRequestFactory`)을 사용하지만, 향후 트래픽 증가 시 `spring.threads.virtual.enabled=true` 한 줄로 처리량을 높일 수 있다.
- **Jakarta EE 11** 네임스페이스를 사용한다(`jakarta.servlet`, `jakarta.validation`, `jakarta.persistence`). Spring Boot 3.x에서 이미 전환됐고 4.x에서 더 안정화됐다.
- **Jackson 3.x** (`tools.jackson`)를 기본으로 채택한다. Spring Boot 4.x의 기본 직렬화 라이브러리이며, 기존 `com.fasterxml.jackson`에서 패키지 네임스페이스가 변경됐다.

**트레이드오프**

- Spring Boot 4.x는 Spring Boot 3.x보다 레퍼런스와 커뮤니티 예제가 적다. 마이그레이션 가이드를 꼼꼼히 확인해야 한다.
- Jackson 3.x 패키지(`tools.jackson`)는 기존 Jackson 2.x 의존성(`com.fasterxml.jackson`)과 혼용 불가하므로 서드파티 라이브러리 호환 여부를 사전 검증해야 한다.

---

## 3. 빌드 도구: Gradle Kotlin DSL (`build.gradle.kts`)

**선택 이유**

- 빌드 스크립트 자체에 IDE 자동완성과 타입 체크가 적용된다. Groovy DSL은 동적 타입이라 오타가 런타임까지 잡히지 않는 반면, Kotlin DSL은 컴파일 시점에 오류를 검출한다.
- Kotlin 프로젝트이므로 언어를 통일해 인지 부하를 줄인다.

---

## 4. 데이터베이스: MySQL + Spring Data JPA

**선택 이유**

- **관계형 DB 선택**: 여행(Trip), 멤버(TripMember), 블록(ScheduleBlock), 예산(BudgetItem) 사이에 명확한 외래 키 관계와 복합 유니크 제약(`trip_id, user_id`)이 존재한다. 스키마가 고정적이고 정합성이 중요하므로 RDB가 적합하다.
- **MySQL**: 범용적인 운영 경험과 클라우드 관리형 서비스(RDS, Cloud SQL) 지원이 풍부하다.
- **JPA + Hibernate**: 엔티티 객체 모델과 DB 스키마를 일치시켜 객체지향적으로 도메인을 표현할 수 있다. `@Version`을 통한 낙관적 잠금, `@PreUpdate`를 통한 자동 타임스탬프 갱신 등 인프라 코드를 줄인다.
- **Spring Data JPA**: `findByEmail`, `findByTripIdAndUserId` 같은 메서드 이름 기반 쿼리로 단순 조회를 선언적으로 작성한다. 복잡한 쿼리만 `@Query`로 보완한다(`findAllWithTripByUserId` JOIN FETCH).

**트레이드오프**

- JPA의 지연 로딩(LAZY)은 N+1 쿼리 문제를 유발할 수 있다. `findAllWithTripByUserId`에서 `JOIN FETCH`로 명시적 해결했지만, 새 쿼리 추가 시마다 주의가 필요하다.
- `@Version` 낙관적 잠금은 충돌 시 예외를 던지므로 클라이언트가 재시도 로직을 갖춰야 한다 (`VersionConflictException` → `409 VERSION_CONFLICT`).

---

## 5. 캐시·세션 저장소: Redis

**선택 이유**

Redis를 두 가지 용도로 사용한다.

| 용도 | 키 패턴 | TTL | 이유 |
|------|---------|-----|------|
| Refresh Token | `refresh:{userId}` | 7일 | stateless JWT에서 토큰 무효화(로그아웃·탈취 대응)를 가능하게 함 |
| AI 생성 결과 | `ai:generation:{generationId}` | 10분 | generate → apply 사이의 임시 결과를 DB 저장 없이 보관 |
| 비밀번호 재설정 코드 | `password-reset:{email}` | 15분 | 인증 코드 단기 보관, TTL 만료로 자동 무효화 |

- **Refresh Token을 Redis에 저장하는 이유**: JWT는 자체적으로 만료 전 무효화가 불가능하다. Redis에 저장하면 로그아웃 시 `delete(key)`만으로 즉시 무효화할 수 있다.
- **AI 결과를 DB가 아닌 Redis에 저장하는 이유**: 사용자가 apply하지 않으면 버려지는 임시 데이터다. TTL 자동 만료로 별도 정리 배치 없이 관리된다.
- **`StringRedisTemplate`**: 값이 모두 문자열(JWT 토큰, JSON)이므로 범용 `RedisTemplate<String, Any>` 대신 경량 버전을 사용한다.

**트레이드오프**

- Redis가 단일 장애점(SPOF)이 될 수 있다. 프로덕션에서는 Redis Sentinel 또는 Cluster 구성이 필요하다.
- 서버 재시작 시 Redis 연결 실패가 로그인·AI 기능 전체 장애로 이어지므로 연결 실패 fallback 전략이 필요하다.

---

## 6. 인증: 직접 구현 JWT (Spring Security 없이)

**선택 이유**

- **Spring Security 미사용**: Spring Security는 강력하지만 Filter Chain, SecurityContext, UserDetails 등 방대한 추상화를 이해해야 한다. 이 프로젝트는 "Bearer 토큰 → userId 추출 → 컨트롤러에 User 주입"이라는 단순한 요구사항만 있으므로, `OncePerRequestFilter` 하나(`JwtFilter`)로 충분하다.
- **`spring-security-crypto`만 의존**: BCrypt 해싱에만 Spring Security의 crypto 모듈을 사용한다. Security 프레임워크 전체를 끌어들이지 않아 의존성 그래프가 단순하다.
- **`@CurrentUser` + ArgumentResolver**: 인증된 사용자를 컨트롤러 파라미터에 직접 주입하는 커스텀 메커니즘이다. SecurityContextHolder 대신 request attribute(`authenticatedUserId`)를 통해 값을 전달해 명시적이고 테스트하기 쉽다.
- **jjwt 0.12.6**: Java JWT 라이브러리 중 가장 널리 쓰이며, 0.12.x부터 fluent API로 빌더 방식이 통일됐다.

**트레이드오프**

- Spring Security가 제공하는 CSRF 보호, XSS 방어 헤더, Method Security 등을 직접 구현해야 한다. 현재는 Refresh Token을 HttpOnly 쿠키로 처리해 XSS 노출은 최소화했지만, CSRF 방어는 없다.
- 추후 권한 모델이 복잡해지면 Spring Security로의 전환 비용이 발생한다.

---

## 7. HTTP 클라이언트: Spring RestClient + JdkClientHttpRequestFactory

**선택 이유**

- **RestClient**: Spring 6.1에서 도입된 동기 HTTP 클라이언트다. 기존 `RestTemplate`의 후속이며 fluent API를 제공한다. 비동기가 필요 없는 외부 API 호출(Anthropic Claude, Google Places, Kakao, Google OAuth)에 적합하다.
- **JdkClientHttpRequestFactory**: Java 11+에 내장된 `java.net.http.HttpClient`를 사용해 별도 라이브러리(Apache HttpClient, OkHttp) 없이 커넥션 타임아웃과 읽기 타임아웃을 설정할 수 있다. 의존성을 최소화하는 선택이다.
- **WebClient 미선택**: WebClient는 Reactor 기반 리액티브 클라이언트로 `spring-boot-starter-webflux`를 요구한다. 이 프로젝트는 서블릿 스택(`spring-boot-starter-web`)을 사용하므로 불필요한 복잡성이 된다.

**트레이드오프**

- 동기 블로킹 방식이므로 Anthropic Claude API(read timeout 30s)처럼 응답이 느린 외부 API 호출이 스레드를 점유한다. 가상 스레드 활성화 시 이 문제가 완화된다.

---

## 8. 실시간 협업: WebSocket + STOMP

**선택 이유**

- **협업 요구사항**: 여러 사용자가 같은 여행 일정을 동시에 편집하므로 서버 → 클라이언트 푸시가 필수다. HTTP 폴링 대비 지연이 낮고 서버 부하가 적다.
- **STOMP over WebSocket**: 순수 WebSocket은 메시지 라우팅을 직접 구현해야 한다. STOMP는 목적지(`/topic/trip.{tripId}`) 기반 pub/sub 모델을 제공해 Spring의 `SimpMessagingTemplate`과 자연스럽게 통합된다.
- **In-memory broker**: 현재 `enableSimpleBroker("/topic")`로 인메모리 브로커를 사용한다. 단일 서버에서는 충분하며, 수평 확장 시 RabbitMQ 또는 Redis pub/sub 기반 외부 브로커로 전환 가능하다.
- **SockJS 폴백**: WebSocket을 지원하지 않는 환경에서 Long Polling 등으로 자동 폴백한다.

**트레이드오프**

- 인메모리 브로커는 서버가 여러 대일 때 메시지가 연결된 서버에만 전달된다. 수평 확장 시 외부 브로커(RabbitMQ STOMP, Redis pub/sub)로 교체해야 한다.

---

## 9. 검증: Jakarta Bean Validation (`spring-boot-starter-validation`)

**선택 이유**

- `@field:Email`, `@field:NotBlank`, `@field:Size`, `@field:Min` 어노테이션으로 DTO 경계에서 입력 검증을 선언적으로 처리한다. 컨트롤러에 검증 로직이 섞이지 않아 서비스 레이어가 비즈니스 로직에 집중할 수 있다.
- Kotlin에서 `@field:` prefix가 필요한 이유: Kotlin의 생성자 파라미터는 프로퍼티이기도 해 어노테이션 타깃이 모호하다. `build.gradle.kts`의 `-Xannotation-default-target=param-property` 컴파일러 옵션으로 이를 해소한다.

---

## 10. 이메일 발송: Spring Mail (선택적)

**선택 이유**

- 비밀번호 재설정 인증 코드를 이메일로 발송한다.
- `@Autowired(required = false)`로 `JavaMailSender`를 선택적 주입한다. 메일 서버 설정이 없는 개발 환경에서도 나머지 기능이 정상 동작한다.
- 발송 실패 시 Redis에 저장된 코드는 유효하고 예외는 catch해 무시한다. 사용자 경험보다 서비스 가용성을 우선한다.

---

## 11. 주요 설계 결정: 낙관적 잠금 + 블록 잠금 이중 전략

**배경**: 여러 사용자가 같은 블록을 동시에 수정할 때 충돌을 방지해야 한다.

**결정**:
- **낙관적 잠금(`@Version`)**: DB 수준에서 동시 수정 충돌을 감지한다. 서버는 `version` 불일치 시 `VersionConflictException`으로 409를 응답하고 현재 서버 상태(`currentBlock`)를 함께 반환해 클라이언트가 UI를 롤백할 수 있게 한다.
- **블록 잠금(`lockedBy`)**: 편집 모달을 열 때 블록을 잠근다(`POST .../lock`). 다른 사용자가 이미 잠근 블록은 423으로 거부한다. 낙관적 잠금이 사후 충돌 감지라면, 블록 잠금은 사전 충돌 예방이다.

두 전략을 조합해 UX(사전 예방으로 충돌 빈도 감소)와 데이터 정합성(사후 감지로 최종 안전망)을 동시에 확보한다.
