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
  connectHeaders: { Authorization: `Bearer ${auth.getToken() ?? ''}` },
  reconnectDelay: 3000,
  onConnect: () => {
    client.subscribe(`/topic/trip.${tripId}`, (frame) => {
      try {
        const msg: WsMessage = JSON.parse(frame.body)
        onMessageRef.current(msg)
      } catch { /* ignore malformed */ }
    })
    // 접속 시 Presence 등록
    publishPresence(client, tripId, activeDayRef.current)
  },
})
client.activate()
```

`reconnectDelay: 3000` — 연결이 끊기면 3초 후 자동 재연결을 시도합니다.

### Presence 발행

```typescript
client.publish({
  destination: `/app/trip.${tripId}.presence`,
  body: JSON.stringify({
    userId: user.id,          // auth.getUser() — App 진입 시 GET /api/auth/me로 저장
    nickname: user.nickname,
    avatarColor: avatarColorOf(user.id),
    activeDay,
  }),
})
```

- 연결 직후, 그리고 활성 Day(`activeDay`)가 바뀔 때마다 발행합니다.
- 현재 유저 정보가 아직 없거나 연결 전이면 발행하지 않습니다.
- `avatarColor`는 userId 해시로 고정 팔레트에서 골라, 같은 유저는 항상 같은 색이 됩니다.
- 퇴장은 따로 발행하지 않습니다. 탭을 닫거나 연결이 끊기면 서버가 접속자에서 제거하고 `PRESENCE`를 다시 브로드캐스트합니다.

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
  setBlocks((prev) => prev.some((b) => b.id === p.id) ? prev : [...prev, p])
```

블록 추가 시 수신됩니다. payload는 `Block` 그대로이며, 내가 추가한 블록은 REST 응답으로 이미 들어가 있으므로 같은 id면 무시합니다.

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

잠긴 블록은 다른 사용자에게 핑크 테두리와 "OO 편집 중" 뱃지로, 잠근 본인에게는 "내가 편집 중" 뱃지로 표시됩니다.

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
  // 요청자에게는 409 응답에서 이미 토스트를 띄우므로 여기서는 상태만 동기화한다
  setBlocks((prev) => prev.map((b) => b.id === payload.blockId ? payload.currentBlock : b))
```

서버가 version 충돌을 감지하면 현재 서버 상태(`currentBlock`)를 담아 CONFLICT를 브로드캐스트합니다. 모든 접속자가 해당 블록을 서버 상태로 맞추고, 토스트는 409를 받은 요청자에게만 한 번 표시됩니다.

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

3-B. 409 VERSION_CONFLICT
   └─ 응답의 currentBlock(서버 최신 상태)으로 교체
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
  } catch (err: any) {
    if (err.response?.data?.code === 'VERSION_CONFLICT') {
      // 3-B. 서버 최신 상태로 교체
      const latest: Block = err.response.data.currentBlock ?? prev
      setBlocks((cur) => cur.map((b) => b.id === blockId ? latest : b))
      toast.error('다른 사용자가 수정했습니다. 최신 내용으로 되돌렸어요.')
    } else {
      // 3-C. 롤백
      setBlocks((cur) => cur.map((b) => b.id === blockId ? prev : b))
      toast.error('저장에 실패했습니다.')
    }
  }
}
```

---

## 잠금(Lock) 흐름

블록 편집 시 충돌을 방지하기 위해 편집 중인 블록을 잠그는 소프트 잠금 방식을 사용합니다.

```
사용자가 블록 카드 클릭
  └─ POST /blocks/:blockId/lock
       ├─ 성공 → BlockModal 열기
       │          └─ (모달 닫힐 때) DELETE /blocks/:blockId/lock
       ├─ 423 ALREADY_LOCKED → 토스트: "OO님이 편집 중입니다."
       └─ 그 외 실패 → 토스트: "편집을 시작하지 못했습니다."
```

`lockedBy`를 현재 유저 id와 비교해 내 잠금과 타인 잠금을 구분합니다.

타인이 잠근 블록은:
- 핑크 테두리(`#D4537E`)로 표시
- "OO 편집 중" 뱃지 노출
- 카드 클릭 편집 · 삭제 버튼 비활성
- `draggable="false"`, 드래그 이벤트 차단

내가 잠근 블록은 "내가 편집 중" 뱃지만 붙고 편집 · 삭제 · 드래그는 그대로 가능합니다.

블록 이동 · 삭제도 서버에서 잠금을 확인합니다. 다른 사용자가 잠근 블록을 옮기거나 삭제하려 하면 `423`이 오고, 로컬 상태를 복구한 뒤 "OO님이 편집 중입니다." 토스트를 띄웁니다.

---

## 에러 / 예외 처리

| 상황 | 처리 |
|---|---|
| WebSocket 연결 끊김 | `reconnectDelay: 3000` — 자동 재연결 |
| 잘못된 JSON 수신 | try/catch로 무시 (parse 실패 시 skip) |
| CONFLICT 수신 | `currentBlock`으로 동기화 (토스트는 409 받은 요청자만) |
| 잠금 획득 실패 (423) | "OO님이 편집 중입니다." 토스트, 모달 미열림 |
