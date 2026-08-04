# DTO 배치 컨벤션

계층 분리(`presentation` / `application`, 외부 API 연동이 있는 도메인은 `client` 포함)를 적용한 도메인에서 DTO를
어느 패키지에 두고 어떻게 부를지 정하는 기준. `ai`, `auth`, `block`, `budget`, `invite`, `member`, `trip` 도메인
모두 이 컨벤션을 따른다.

---

## 계층별 역할

| 위치 | 역할 | 예시 |
|------|------|------|
| `presentation/dto/` | 컨트롤러의 요청·응답 DTO. `Request` / `Response` | `AuthRequest`, `TokenResponse`, `AiResponse` |
| `application/dto/` | 서비스가 주고받는 DTO. `Query`(조회 조건) / `Command`(생성·수정·삭제 입력) / `Result`(controller에 돌려주는 값) | `AuthCommand`, `TokenPair` |
| `client/dto/` | 외부 API 클라이언트가 **공개 반환값**으로 노출하는 DTO | `KakaoUserInfo`, `GoogleUserInfo` |

**presentation의 DTO를 application 레이어 메서드 시그니처에 그대로 넘기지 않는다.** 컨트롤러가 `Request`를 받아
그에 대응하는 `Command`(또는 `Query`)로 변환해 서비스를 호출한다. 서비스는 presentation 패키지를 참조하지 않는다.

```kotlin
// presentation/AuthController.kt
fun signup(@RequestBody request: AuthRequest.Signup): TokenResponse {
    val result = authService.signup(AuthCommand.Signup(request.email, request.password, request.nickname))
    ...
}

// application/AuthService.kt — presentation.dto를 import하지 않는다
fun signup(command: AuthCommand.Signup): TokenPair { ... }
```

단일 primitive 파라미터(예: OAuth `code: String`, `refreshToken: String`)는 필드가 하나뿐이라 굳이 `Command`/`Query`로
감싸지 않는다. 여러 필드를 묶어 하나의 입력으로 다뤄야 할 때만 감싼다.

`Result`는 역할이지 강제 네이밍이 아니다. `TokenPair`처럼 이미 의미가 분명한 도메인 이름이 있다면 그대로 쓰고,
마땅한 이름이 없을 때만 `XxxResult`로 짓는다.

---

## client 내부 wire-format DTO

client 내부에서만 쓰는 wire-format 파싱용 struct는 `dto/`로 승격하지 않는다. 외부로 노출되지 않고 클라이언트 클래스
안에서 응답을 파싱하는 용도로만 쓰인다면 그 클라이언트 클래스 안에 `private data class`로 둔다.

```kotlin
// client/AnthropicClient.kt — 외부에 노출 안 됨 → private, dto/로 옮기지 않음
private data class MessagesResponse(val content: List<ContentBlock>)

// client/KakaoOAuthClient.kt — fetchUserInfo()가 공개 반환 → client/dto/KakaoUserInfo.kt
data class KakaoUserInfo(val id: String, val email: String?, val nickname: String)
```

판단 기준은 하나다: **그 DTO를 만든 함수를 호출한 쪽(다른 클래스)이 그 타입을 직접 참조하는가?**
참조한다면 `dto/`로 승격하고, 그 DTO를 만든 계층의 `dto/` 아래에 둔다. 그 계층 안에서만 쓰이고 끝난다면 승격하지 않는다.

---

## persistence(entity)

JPA 엔티티를 쓰기·읽기 양쪽에 그대로 사용하므로 별도의 영속성 DTO 계층을 두지 않는다. 대신 **엔티티는 application까지만
등장할 수 있고, 그 위(presentation)로는 역류하지 않는다.** application은 repository에서 꺼낸 엔티티를 그대로 리턴하지
않고, `Result`로 필요한 필드만 뽑아 변환한 뒤 presentation에 넘긴다.

```kotlin
// application/AuthService.kt
fun signup(command: AuthCommand.Signup): TokenPair {   // TokenPair(Result) — User 엔티티 자체는 리턴하지 않음
    val user = userRepository.save(User(...))
    return issueTokenPair(user.id)
}
```

예외: `@CurrentUser`처럼 presentation이 인증된 사용자 정보를 얻기 위해 리졸버로 엔티티를 직접 조회하는 패턴은 이 규칙
밖에 있다(모든 도메인이 공유하는 기존 패턴이라 이번 정리 범위에 포함하지 않음).

---

## 묶음 규칙

같은 컨트롤러(또는 서비스)가 다루는 DTO가 여러 개고 서로 관련 있다면, `object`로 묶어 하나의 파일에 둔다.

```kotlin
object AuthRequest {
    data class Login(...)
    data class Signup(...)
}

object AuthCommand {
    data class Login(...)
    data class Signup(...)
}
```

DTO가 하나뿐이면 굳이 `object`로 감싸지 않고 top-level `data class`로 둔다 (`TokenResponse`, `UserResponse`, `TokenPair`).
