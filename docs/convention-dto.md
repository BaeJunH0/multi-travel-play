# DTO 배치 컨벤션

계층 분리(`presentation` / `application` / `client`)를 적용한 도메인에서 DTO를 어느 패키지에 둘지 정하는 기준.
`ai`, `auth` 도메인에 적용되어 있다. `block`/`budget`/`trip`/`member`/`invite`처럼 계층 분리가 없는 flat 구조 도메인은
지금처럼 도메인 루트의 `dto/`에 요청·응답 DTO를 모아두면 된다(이 문서의 대상 아님).

---

## 기준

| 위치 | 대상 | 예시 |
|------|------|------|
| `presentation/dto/` | 컨트롤러의 요청·응답 DTO | `AuthRequest`, `TokenResponse`, `AiResponse` |
| `application/dto/` | 여러 계층을 오가며 재사용되는 내부 전달용 DTO. 서비스가 만들어 controller 등 다른 계층에 돌려주는 값 | `TokenPair` |
| `client/dto/` | 외부 API 클라이언트가 **공개 반환값**으로 노출하는 DTO | `KakaoUserInfo`, `GoogleUserInfo` |

**client 내부에서만 쓰는 wire-format 파싱용 struct는 `dto/`로 승격하지 않는다.** 외부로 노출되지 않고 클라이언트 클래스
안에서 응답을 파싱하는 용도로만 쓰인다면 그 클라이언트 클래스 안에 `private data class`로 둔다.

```kotlin
// client/OpenAiClient.kt — 외부에 노출 안 됨 → private, dto/로 옮기지 않음
private data class ChatCompletionResponse(val choices: List<Choice>)

// client/KakaoOAuthClient.kt — fetchUserInfo()가 공개 반환 → client/dto/KakaoUserInfo.kt
data class KakaoUserInfo(val id: String, val email: String?, val nickname: String)
```

판단 기준은 하나다: **그 DTO를 만든 함수를 호출한 쪽(다른 클래스)이 그 타입을 직접 참조하는가?**
참조한다면 `dto/`로 승격하고, 그 DTO를 만든 계층의 `dto/` 아래에 둔다. 그 계층 안에서만 쓰이고 끝난다면 승격하지 않는다.

---

## Request/Response 묶음 규칙

같은 컨트롤러가 다루는 요청(또는 응답) DTO가 여러 개고 서로 관련 있다면, `object`로 묶어 하나의 파일에 둔다.

```kotlin
object AuthRequest {
    data class Login(...)
    data class Signup(...)
}
```

DTO가 하나뿐이면 굳이 `object`로 감싸지 않고 top-level `data class`로 둔다 (`TokenResponse`, `UserResponse`).
