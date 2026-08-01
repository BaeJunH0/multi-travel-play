# websocket 패키지

실시간 협업을 위한 STOMP 기반 WebSocket 이벤트 발행 및 Presence(접속자 현황) 관리 패키지.

---

## WebSocket 엔드포인트

| 종류 | 경로 |
|------|------|
| 연결 (SockJS) | `/ws` |
| 구독 (수신) | `/topic/trip.{tripId}` |
| Presence 발행 (송신) | `/app/trip.{tripId}.presence` |

클라이언트는 `/topic/trip.{tripId}` 를 구독하면 해당 여행의 모든 이벤트를 수신한다.

---

## 이벤트 공통 형식

```json
{
  "action": "ADD | UPDATE | MOVE | DELETE | LOCK | UNLOCK | CONFLICT | PRESENCE",
  "tripId": "uuid",
  "payload": { }
}
```

---

## 이벤트 종류별 payload

| action | 발생 시점 | payload 구조 |
|--------|----------|-------------|
| `ADD` | 블록 추가 | `BlockResponse` 객체 전체 |
| `UPDATE` | 블록 수정 | `{ blockId, block: BlockResponse }` |
| `MOVE` | 블록 이동 (DnD) | `{ blockId, dayNumber, position, version }` |
| `DELETE` | 블록 삭제 | `{ blockId }` |
| `LOCK` | 잠금 획득 | `{ blockId, lockedBy, lockedByNickname }` |
| `UNLOCK` | 잠금 해제 | `{ blockId }` |
| `CONFLICT` | version 충돌 | `{ blockId, currentBlock: BlockResponse }` |
| `PRESENCE` | 접속자 변경 | `{ users: [{ userId, nickname, avatarColor, activeDay }] }` |

**충돌 처리 흐름**: 클라이언트가 Optimistic Update를 적용한 뒤 `CONFLICT` 이벤트를 수신하면 `currentBlock`(서버 최신 상태)으로 롤백한다. REST `409` 응답과 동시에 `CONFLICT` 이벤트가 브로드캐스트된다.

---

## Presence 동작 방식

`PresenceStore` 는 인메모리 `ConcurrentHashMap`으로 접속자 목록을 관리한다.

```
store: Map<tripId, Map<userId, PresenceUser>>
```

- **join**: 클라이언트가 `/app/trip.{tripId}.presence` 로 메시지를 보내면 `PresenceController`가 `presenceStore.join()` 을 호출해 유저 정보를 upsert한다.
- **leave**: `presenceStore.leave(tripId, userId)` 로 제거한다 (현재 WebSocket disconnect 훅에서 호출 예정).
- join 이후 즉시 해당 trip 전체 접속자 목록을 `PRESENCE` 이벤트로 브로드캐스트한다.

**Presence 요청 형식** (클라이언트 → 서버):
```json
{
  "userId": "uuid",
  "nickname": "string",
  "avatarColor": "string",
  "activeDay": 2
}
```

`activeDay` 는 현재 보고 있는 일차 (nullable). 화면 이동 시 재발행하여 커서 위치를 공유한다.

---

## 이벤트 발행 주체

`BlockController` (및 `LockController`) → `TripEventPublisher.publish(tripId, event)`

`TripEventPublisher` 는 `SimpMessagingTemplate.convertAndSend("/topic/trip.{tripId}", event)` 를 래핑한 단순 컴포넌트다. 직접 `SimpMessagingTemplate` 를 쓰지 말고 반드시 `TripEventPublisher` 를 통해 발행한다.

`TripEvent` 의 팩토리 메서드(`add`, `update`, `move`, `delete`, `lock`, `unlock`, `conflict`)를 사용해 이벤트를 생성한다. payload 타입은 `TripEvent.kt` 내 sealed data class들로 고정되어 있다.
