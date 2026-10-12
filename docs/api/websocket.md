> 공통 사항(Base URL, 에러 응답 형식 등)은 [README.md](./README.md) 참고

# WebSocket (STOMP)

### 연결
```
SockJS endpoint: /ws
구독 destination: /topic/trip.{tripId}
Presence 발행:   /app/trip.{tripId}.presence
```

### 수신 이벤트 형식
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
| `UNLOCK` | 잠금 해제 | `{ blockId }` |
| `CONFLICT` | version 충돌 (**현재 미발행**) | `{ blockId, currentBlock }` |
| `PRESENCE` | 접속자 변경 | `{ users: [{ userId, nickname, avatarColor, activeDay }] }` |

### Presence 발행 형식
```json
{
  "userId": "uuid",
  "nickname": "string",
  "avatarColor": "string",
  "activeDay": 2
}
```

- 블록 이벤트(`ADD`~`UNLOCK`)는 REST 요청이 성공한 뒤에만 발행된다. `reorder`는 이벤트를 발행하지 않는다.
- `PRESENCE`는 presence 발행 시마다 해당 trip의 전체 접속자 목록을 브로드캐스트한다. 접속 종료(leave) 처리는 아직 연결되어 있지 않아, 한 번 등록된 접속자는 서버 메모리에서 제거되지 않는다.

### 충돌 처리
- 블록마다 `version` 관리
- `version` 불일치 시 REST `409 VERSION_CONFLICT` 응답 (body에 `currentBlock` 포함)
- `CONFLICT` 이벤트는 action·payload 타입만 정의되어 있고 **현재 어디서도 브로드캐스트하지 않는다** — 충돌한 요청자만 REST `409`로 알 수 있다
- 클라이언트는 Optimistic Update 후 `409` 수신 시 `currentBlock`(서버 상태)으로 롤백
