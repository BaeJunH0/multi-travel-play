# 실시간 이벤트 (SSE)

## 개요

블록 에디터는 다른 사용자의 변경을 SSE(Server-Sent Events)로 받아 화면에 반영합니다. 서버 → 클라이언트 단방향이며, 클라이언트의 변경은 모두 REST로 보냅니다.

- 구독 엔드포인트: `GET /api/trips/{tripId}/events` (`text/event-stream`)
- 클라이언트: `@microsoft/fetch-event-source` (헤더로 accessToken을 보내기 위해 브라우저 `EventSource` 대신 사용)
- 활성 Day 변경: `POST /api/trips/{tripId}/presence`
- 개발 서버에서는 Vite의 `/api` 프록시를 그대로 탑니다 (스트림을 버퍼링하지 않음)

---

## 연결 설정 (`src/hooks/useTripEvents.ts`)

```typescript
fetchEventSource(`/api/trips/${tripId}/events`, {
  signal: ctrl.signal,
  openWhenHidden: true,
  // 재연결할 때마다 최신 accessToken을 붙인다
  fetch: (input, init) => {
    const headers = new Headers(init?.headers)
    headers.set('Authorization', `Bearer ${auth.getToken() ?? ''}`)
    return fetch(input, { ...init, headers })
  },
  async onopen(res) {
    if (res.ok) { sendPresence(tripId, activeDayRef.current); return }
    if (res.status === 401) { await authApi.me(); throw new Error('unauthorized') }
    throw new FatalError(`SSE 연결 실패: ${res.status}`)
  },
  onmessage(ev) {
    if (!ev.data) return
    try { onMessageRef.current(JSON.parse(ev.data)) } catch { /* ignore malformed */ }
  },
  onclose() { throw new Error('closed') },
  onerror(err) {
    if (err instanceof FatalError) throw err
    return RETRY_DELAY_MS  // 3000
  },
})
```

- 연결이 끊기거나 서버가 스트림을 닫으면 3초 후 자동 재연결합니다.
- `401`이면 accessToken이 만료된 것이므로 `authApi.me()`를 호출해 axios 인터셉터의 refresh 흐름(`services/api.ts`)을 태운 뒤 재연결합니다. refresh도 실패하면 인터셉터가 로그인 페이지로 보냅니다.
- `403` · `404` 등은 재시도하지 않습니다.
- `openWhenHidden: true` — 기본값이면 탭이 숨겨질 때 연결을 끊는데, 마지막 연결이 끊기면 서버가 내 잠금을 해제하고 접속자에서 빼므로 탭을 옮겨도 연결을 유지합니다.
- 서버는 30초마다 heartbeat(SSE 주석)를 보내지만 `onmessage`로는 오지 않습니다.
- 컴포넌트 언마운트 시 `AbortController`로 연결을 끊습니다.

### Presence

- 접속자 등록은 서버가 연결 시 자동으로 합니다. `nickname`은 유저 정보, `avatarColor`는 userId 해시로 서버가 골라 같은 유저는 항상 같은 색이 됩니다.
- 클라이언트는 활성 Day만 보냅니다: 연결이 열린 직후, 그리고 활성 Day(`activeDay`)가 바뀔 때마다 `tripApi.updatePresence(tripId, activeDay)` (`POST /api/trips/{tripId}/presence`). 연결 전에 보낸 요청은 서버가 무시합니다.
- 퇴장은 따로 보내지 않습니다. 탭을 닫거나 연결이 끊기면 서버가 접속자에서 제거하고 `PRESENCE`를 다시 브로드캐스트합니다.

---

## 메시지 형식

```typescript
interface TripEventPayloads {
  ADD: Block
  UPDATE: { blockId: string; block: Block }
  MOVE: { blockId: string; dayNumber: number; position: number; version: number }
  DELETE: { blockId: string }
  LOCK: { blockId: string; lockedBy: string | null; lockedByNickname: string | null }
  UNLOCK: { blockId: string }
  CONFLICT: { blockId: string; currentBlock: Block }
  PRESENCE: { users: Presence[] }
}

type TripEventAction = keyof TripEventPayloads

// action마다 payload 형식이 정해진 유니온. action으로 분기하면 payload 타입이 좁혀진다
type TripEvent = {
  [A in TripEventAction]: { action: A; tripId: string; payload: TripEventPayloads[A] }
}[TripEventAction]
```

SSE `data:` 한 줄이 이 형식의 JSON 하나입니다. `PRESENCE`는 `TripEditorPage`가, 나머지는 `useTripBlocks`의 `handleTripEvent`가 처리합니다.

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

잠금 해제 API 외에, 잠금자의 연결이 끊기거나 잠금이 만료되어 서버가 해제할 때도 수신됩니다.

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

잠금은 무기한 남지 않습니다. 잠금자의 마지막 SSE 연결이 끊기면 서버가 즉시 해제하고, 연결이 살아 있지 않은 잠금은 3분(안전망 TTL) 뒤 해제됩니다. 어느 쪽이든 `UNLOCK`이 와서 다른 사용자의 화면에서 잠금 표시가 사라집니다.

블록 이동 · 삭제도 서버에서 잠금을 확인합니다. 다른 사용자가 잠근 블록을 옮기거나 삭제하려 하면 `423`이 오고, 로컬 상태를 복구한 뒤 "OO님이 편집 중입니다." 토스트를 띄웁니다.

---

## 에러 / 예외 처리

| 상황 | 처리 |
|---|---|
| SSE 연결 끊김 · 서버가 스트림 종료 | 3초 후 자동 재연결 |
| SSE 연결 `401` | `authApi.me()`로 토큰 갱신(인터셉터) 후 재연결 |
| SSE 연결 `403` · `404` 등 | 재연결 중단 |
| 잘못된 JSON 수신 | try/catch로 무시 (parse 실패 시 skip) |
| CONFLICT 수신 | `currentBlock`으로 동기화 (토스트는 409 받은 요청자만) |
| 잠금 획득 실패 (423) | "OO님이 편집 중입니다." 토스트, 모달 미열림 |
