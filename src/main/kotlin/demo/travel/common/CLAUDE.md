# common 패키지

전역 예외 클래스와 예외 핸들러를 제공하는 공통 인프라 패키지.

## 예외 클래스

| 클래스 | 용도 |
|---|---|
| `BusinessException` | 도메인 규칙 위반 등 일반 비즈니스 오류. `code`, `message`, `status`(기본 400)를 직접 지정. |
| `VersionConflictException` | 낙관적 잠금 충돌 시 사용. 충돌 시점의 최신 `BlockResult`를 함께 전달. 메시지는 고정("다른 사용자가 이미 수정했습니다."). |

## GlobalExceptionHandler 처리 목록

`@RestControllerAdvice`로 등록되며 세 종류의 예외를 처리한다.

| 예외 | HTTP 상태 | 응답 형태 |
|---|---|---|
| `ResponseStatusException` | 예외에 명시된 상태 코드 | `ErrorResponse(code, message)` — 상태 코드를 아래 규칙으로 변환 |
| `BusinessException` | 예외에 명시된 `status` | `ErrorResponse(code, message)` |
| `VersionConflictException` | 409 Conflict | `{code, message, currentBlock}` |

### HTTP 상태 코드 → 에러 코드 변환 (`toErrorCode`)

| 상태 | 코드 |
|---|---|
| 400 | `INVALID_REQUEST` |
| 401 | `UNAUTHORIZED` |
| 403 | `FORBIDDEN` |
| 404 | `NOT_FOUND` |
| 409 | `CONFLICT` |
| 423 | `ALREADY_LOCKED` |
| 그 외 | `INTERNAL_ERROR` |
