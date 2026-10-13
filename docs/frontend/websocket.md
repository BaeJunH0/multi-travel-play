# WebSocket (실시간 협업)

## 개요

블록 에디터는 여러 사용자가 동시에 편집할 수 있도록 WebSocket을 통한 실시간 동기화를 지원합니다.

- 전송 프로토콜: **STOMP** over **SockJS**
- 연결 엔드포인트: `/ws`
- 구독 토픽: `/topic/trip.{tripId}`
- Presence 발행: `/app/trip.{tripId}.presence`

---

## 연결 설정 (`src/hooks/useWebSocket.ts`)

```typescript
const client = new Client({
  webSocketFactory: () => new SockJS('/ws'),
  connectHeaders: { Authorization: `Bearer ${auth.getToken()}` },
  reconnectDelay: 3000,
  onConnect: () => {
    client.subscribe(`/topic/trip.${tripId}`, (frame) => {
      const msg: WsMessage = JSON.parse(frame.body)
      onMessage(msg)
    })
    // 접속 시 Presence 등록
    client.publish({ destination: `/app/trip.${tripId}.presence` })
  },
})
client.activate()
```

`reconnectDelay: 3000` — 연결이 끊기면 3초 후 자동 재연결을 시도합니다.

컴포넌트 언마운트 시 `client.deactivate()`를 호출해 연결을 정리합니다.

---

## 메시지 형식

```typescript
interface WsMessage {
  action: WsAction
  tripId: string
  payload: WsPayload
}

type WsAction = 'ADD' | 'UPDATE' | 'MOVE' | 'DELETE' | 'LOCK' | 'UNLOCK' | 'CONFLICT' | 'PRESENCE'
```

---

## 이벤트별 처리 (`src/hooks/useTripBlocks.ts`)

### ADD — 블록 추가

```typescript
case 'ADD':
  setBlocks((prev) => [...prev, payload.block])
```

다른 사용자가 블록을 추가했을 때 수신됩니다.

### UPDATE — 블록 수정

```typescript
case 'UPDATE':
  setBlocks((prev) => prev.map((b) => b.id === payload.blockId ? payload.block : b))
```

### MOVE — 블록 이동

```typescript
case 'MOVE':
  setBlocks((prev) => prev.map((b) =>
    b.id === payload.blockId
      ? { ...b, dayNumber: payload.dayNumber, position: payload.position, version: payload.version }
      : b
  ))
```

전체 Block 객체 대신 변경 필드만 전송하여 네트워크 비용을 줄입니다.

### DELETE — 블록 삭제

```typescript
case 'DELETE':
  setBlocks((prev) => prev.filter((b) => b.id !== payload.blockId))
```

### LOCK — 잠금 획득

```typescript
case 'LOCK':
  setBlocks((prev) => prev.map((b) =>
    b.id === payload.blockId
      ? { ...b, lockedBy: payload.lockedBy, lockedByNickname: payload.lockedByNickname }
      : b
  ))
```

잠긴 블록은 다른 사용자에게 핑크 테두리와 "OO 편집 중" 뱃지로 표시됩니다.

### UNLOCK — 잠금 해제

```typescript
case 'UNLOCK':
  setBlocks((prev) => prev.map((b) =>
    b.id === payload.blockId
      ? { ...b, lockedBy: null, lockedByNickname: null }
      : b
  ))
```

### CONFLICT — 충돌 감지

```typescript
case 'CONFLICT':
  setBlocks((prev) => prev.map((b) => b.id === payload.blockId ? payload.currentBlock : b))
  toast.error('다른 사용자가 수정했습니다. 최신 내용으로 되돌렸어요.')
```

서버가 version 충돌을 감지하면 현재 서버 상태(`currentBlock`)를 담아 CONFLICT를 브로드캐스트합니다. 클라이언트는 로컬 상태를 서버 상태로 롤백합니다.

### PRESENCE — 접속자 현황

```typescript
// TripEditorPage에서 별도 처리
if (msg.action === 'PRESENCE') {
  setPresence(payload.users)
}
```

```typescript
interface Presence {
  userId: string
  nickname: string
  avatarColor: string
  activeDay?: number | null
}
```

TopBar의 "N명 편집 중" 카운트와 AvatarStack이 이 목록으로 렌더링됩니다.

---

## Optimistic Update 패턴

네트워크 지연 없이 즉각적인 UI 반응을 위해 낙관적 업데이트를 적용합니다.

```
1. 사용자 액션 발생
   └─ 로컬 상태 즉시 업데이트 (낙관적 적용)

2. REST API 호출 (PATCH /blocks/:id)

3-A. 성공
   └─ 서버 응답으로 로컬 상태를 최종 확정

3-B. 409 Conflict
   └─ 로컬 상태를 이전 값으로 롤백
       └─ 토스트: "다른 사용자가 수정했습니다"

3-C. 그 외 오류
   └─ 로컬 상태 롤백
       └─ 토스트: "저장에 실패했습니다"
```

### 구현 예시 (updateBlock)

```typescript
const updateBlock = async (blockId: string, patch: Partial<Block>) => {
  const prev = blocks.find((b) => b.id === blockId)!
  // 1. 낙관적 적용
  setBlocks((cur) => cur.map((b) => b.id === blockId ? { ...b, ...patch } : b))
  try {
    // 2. API 호출
    const { data: updated } = await blockApi.updateBlock(tripId, blockId, {
      ...patch, version: prev.version
    })
    // 3-A. 서버 응답으로 확정
    setBlocks((cur) => cur.map((b) => b.id === blockId ? updated : b))
  } catch (err) {
    // 3-B/C. 롤백
    setBlocks((cur) => cur.map((b) => b.id === blockId ? prev : b))
    toast.error(err.response?.status === 409
      ? '다른 사용자가 수정했습니다. 최신 내용으로 되돌렸어요.'
      : '저장에 실패했습니다.')
  }
}
```

---

## 잠금(Lock) 흐름

블록 편집 시 충돌을 방지하기 위해 편집 중인 블록을 잠그는 소프트 잠금 방식을 사용합니다.

```
사용자가 편집 버튼 클릭
  └─ POST /blocks/:blockId/lock
       ├─ 성공 → BlockModal 열기
       │          └─ (모달 닫힐 때) DELETE /blocks/:blockId/lock
       └─ 실패 → 토스트: "다른 사용자가 편집 중입니다"
```

잠긴 블록은:
- 핑크 테두리(`#D4537E`)로 표시
- "OO 편집 중" 뱃지 노출
- 편집/삭제 버튼 숨김
- `draggable="false"`, 드래그 이벤트 차단

---

## 에러 / 예외 처리

| 상황 | 처리 |
|---|---|
| WebSocket 연결 끊김 | `reconnectDelay: 3000` — 자동 재연결 |
| 잘못된 JSON 수신 | try/catch로 무시 (parse 실패 시 skip) |
| CONFLICT 수신 | 롤백 + 토스트 |
| 잠금 획득 실패 | 토스트 알림, 모달 미열림 |
