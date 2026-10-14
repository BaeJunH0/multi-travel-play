> 공통 사항(Base URL, 에러 응답 형식 등)은 [README.md](./README.md) 참고

# 실시간 이벤트 (SSE)

### 이벤트 구독
```
GET /api/trips/{tripId}/events
Accept: text/event-stream
Authorization: Bearer {accessToken}
```
**응답** `200 OK` `Content-Type: text/event-stream` — 연결을 유지한 채 이벤트를 계속 보낸다
- 헤더 인증이 필요하므로 브라우저 `EventSource` 대신 fetch 기반 SSE 클라이언트를 쓴다 (토큰을 URL 쿼리에 넣지 않는다)
- 인증 실패 `401`, 여행 멤버가 아니면 `403` (VIEWER도 구독 가능). 이 엔드포인트의 에러 응답은 상태 코드만 의미가 있다 (`Accept: text/event-stream`이라 JSON 본문을 보장하지 않음)
- 연결 시간 제한 없음. 서버가 스트림을 닫으면(재시작 등) 클라이언트가 다시 연결한다
- 단일 서버 메모리에 연결을 보관한다 (수평 확장 미지원)

### 이벤트 형식
각 이벤트는 `data:` 한 줄에 JSON으로 온다 (이벤트 이름 없음).
```
data: {"action":"MOVE","tripId":"uuid","payload":{ }}
```
```json
{
  "action": "ADD | UPDATE | MOVE | DELETE | LOCK | UNLOCK | CONFLICT | PRESENCE",
  "tripId": "uuid",
  "payload": { }
}
```

### 이벤트별 payload

| action | 발생 시점 | payload |
|--------|---------|---------|
| `ADD` | 블록 추가 | Block 객체 전체 |
| `UPDATE` | 블록 수정 | `{ blockId, block }` |
| `MOVE` | 블록 이동 | `{ blockId, dayNumber, position, version }` |
| `DELETE` | 블록 삭제 | `{ blockId }` |
| `LOCK` | 잠금 획득 | `{ blockId, lockedBy, lockedByNickname }` |
| `UNLOCK` | 잠금 해제 (직접 해제 · 연결 종료 · 만료) | `{ blockId }` |
| `CONFLICT` | 블록 수정·이동 시 version 충돌 | `{ blockId, currentBlock }` |
| `PRESENCE` | 접속자 변경 | `{ users: [{ userId, nickname, avatarColor, activeDay }] }` |

- 블록 이벤트(`ADD`~`DELETE`, `LOCK`)는 REST 요청이 성공한 뒤에만 발행된다. `CONFLICT`는 수정·이동 요청이 version 충돌로 실패했을 때 발행된다. `reorder`는 이벤트를 발행하지 않는다.
- `UNLOCK`은 잠금 해제 API 외에, 잠금자의 마지막 연결이 끊기거나 잠금이 만료되어 서버가 해제할 때도 발행된다 (블록 문서의 "잠금 자동 해제" 참고).

### Heartbeat
- 서버는 30초마다 모든 연결에 SSE 주석(`:heartbeat`)을 보낸다. 클라이언트에는 이벤트로 전달되지 않는다
- 전송에 실패한 연결은 끊긴 것으로 보고 정리한다 (아래 연결 종료 처리)

### Presence
- 연결하면 접속자로 자동 등록되고 해당 trip의 전체 접속자 목록이 `PRESENCE`로 브로드캐스트된다
  - `nickname`은 유저 정보, `avatarColor`는 userId로 서버가 계산한다 (같은 유저는 항상 같은 색). 처음 `activeDay`는 `null`
  - 같은 유저가 이미 다른 탭으로 접속 중이면 기존 정보를 유지한다
- 연결이 끊기면(탭 닫기·네트워크 끊김 포함) 그 유저의 해당 trip **마지막 연결**일 때만 목록에서 빠지고 `PRESENCE`가 브로드캐스트된다

### 활성 Day 변경
```
POST /api/trips/{tripId}/presence
```
**요청**
```json
{ "activeDay": 2 }
```
**응답** `204 No Content`
- 접속 중인 유저의 `activeDay`를 바꾸고 `PRESENCE`를 브로드캐스트한다
- 이벤트 구독(연결) 중이 아니면 아무것도 바뀌지 않는다 (`204`). 그래서 클라이언트는 연결이 열린 직후 현재 활성 Day를 한 번 보낸다

### 연결 종료 처리
유저의 해당 trip 마지막 연결이 끊기면:
1. 접속자 목록에서 제거 → `PRESENCE`
2. 그 유저가 그 trip에서 잡고 있던 블록 잠금을 모두 해제 → 블록마다 `UNLOCK`

### 충돌 처리
- 블록마다 `version` 관리
- `version` 불일치 시 REST `409 VERSION_CONFLICT` 응답(body에 `currentBlock` 포함) + `CONFLICT` 이벤트 브로드캐스트
- 클라이언트는 Optimistic Update 후 `409` 또는 `CONFLICT` 수신 시 `currentBlock`(서버 상태)으로 롤백
