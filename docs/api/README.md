# 여행 플래너 — API 스펙

## 공통

- Base URL: `/api`
- 인증: `Authorization: Bearer {accessToken}` (명시된 예외 제외 모든 엔드포인트)
- Content-Type: `application/json`
- 에러 응답 형식:
  ```json
  { "code": "ERROR_CODE", "message": "설명" }
  ```

## 도메인별 문서

| 도메인 | 문서 |
|--------|------|
| 인증 (Auth) | [auth.md](./auth.md) |
| 여행 (Trip) | [trip.md](./trip.md) |
| 블록 / 잠금 (Block / Lock) | [block.md](./block.md) |
| 초대 (Invite) | [invite.md](./invite.md) |
| 멤버 (Member) | [member.md](./member.md) |
| 예산 (Budget) | [budget.md](./budget.md) |
| AI 일정 생성 | [ai.md](./ai.md) |
| WebSocket (STOMP) | [websocket.md](./websocket.md) |

---

## 에러 코드

| HTTP | code | 상황 |
|------|------|------|
| 400 | `INVALID_REQUEST` | 요청 형식 오류 |
| 401 | `UNAUTHORIZED` | 인증 토큰 없음 / 만료 |
| 403 | `FORBIDDEN` | 권한 없음 (VIEWER가 편집 시도 등) |
| 404 | `NOT_FOUND` | 리소스 없음 |
| 409 | `CONFLICT` | 리소스 중복 (예: 이메일 중복 가입) |
| 409 | `VERSION_CONFLICT` | 블록 version 불일치 |
| 423 | `ALREADY_LOCKED` | 다른 사용자가 블록 잠금 중 |
| 400 | `INVALID_RESET_TOKEN` | 비밀번호 재설정 코드 불일치 또는 만료 |
| 403 | `SOCIAL_ACCOUNT` | 소셜 로그인 계정은 비밀번호 변경 불가 |
| 500 | `INTERNAL_ERROR` | 서버 오류 |
