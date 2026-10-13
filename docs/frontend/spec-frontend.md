# 여행 플래너 — Frontend 스펙

## 기술 스택

| 영역 | 기술 |
|------|------|
| Language | TypeScript |
| Framework | React 18 |
| Styling | Tailwind CSS |
| 실시간 | STOMP.js + SockJS |
| 상태 관리 | React 내장 (useState / useReducer / Context) |
| HTTP | Axios |
| DnD | HTML5 Drag and Drop API |

---

## 화면 목록

| 화면 | 경로 | 설명 |
|------|------|------|
| 로그인 | `/login` | OAuth 소셜 로그인 |
| 여행 목록 | `/` | 내 여행 카드 목록 |
| 블록 에디터 | `/trips/{tripId}` | 메인 편집 화면 |
| 초대 수락 | `/invite/{shareToken}` | 공유 링크 진입 후 참여 처리 |

---

## 컴포넌트 구조

```
src/
├── pages/
│   ├── LoginPage.tsx
│   ├── TripListPage.tsx
│   ├── TripEditorPage.tsx
│   └── InvitePage.tsx
├── components/
│   ├── TripBoard.tsx           -- Day 컬럼 전체를 감싸는 보드
│   ├── DayColumn.tsx           -- Day 단위 컬럼 (드롭 존)
│   ├── BlockCard.tsx           -- 개별 블록 카드 (드래그 소스)
│   ├── modals/
│   │   ├── BlockModal.tsx      -- 블록 추가 / 편집 모달
│   │   └── AiModal.tsx         -- AI 생성 3단계 모달
│   └── ui/
│       ├── BlockTypeBadge.tsx  -- 블록 유형 뱃지 (색상 분기)
│       └── AvatarStack.tsx     -- 실시간 접속자 아바타
├── hooks/
│   ├── useWebSocket.ts         -- STOMP 연결 · 구독 · 발행
│   ├── useTripBlocks.ts        -- 블록 목록 상태 관리
│   └── useDragAndDrop.ts       -- DnD 핸들러
└── services/
    ├── tripApi.ts
    └── blockApi.ts
```

---

## 블록 타입 정의

```typescript
type BlockType = 'HOTEL' | 'FOOD' | 'CAFE' | 'PLACE' | 'TRANSPORT'

interface Block {
  id: string
  dayNumber: number
  position: number
  blockType: BlockType
  placeName: string
  lat: number | null
  lng: number | null
  startTime: string | null    // "HH:mm"
  durationMin: number | null
  cost: number | null
  memo: string | null
  lockedBy: string | null     // userId, null이면 잠금 해제
  lockedByNickname: string | null
  version: number
}
```

### 블록 타입별 색상
| blockType | 배경색 | 좌측 보더 | 뱃지 배경 |
|-----------|--------|-----------|-----------|
| HOTEL | `#E1F5EE` | `#1D9E75` | `#9FE1CB` |
| FOOD | `#FAECE7` | `#D85A30` | `#F5C4B3` |
| CAFE | `#FAEEDA` | `#BA7517` | `#FAC775` |
| PLACE | `#EEEDFE` | `#7F77DD` | `#CECBF6` |
| TRANSPORT | `#E6F1FB` | `#378ADD` | `#B5D4F4` |

---

## 화면별 상세 스펙

### 1. 블록 에디터 (`/trips/{tripId}`)

#### 상단 TopBar
- 여행 제목 표시
- 실시간 접속자 수 표시 ("N명 편집 중" + 초록 점)
- 접속자 아바타 스택 (최대 3개, 초과 시 "+N" 표시)
- 공유 버튼: 클릭 시 초대 링크 클립보드 복사
- AI 생성 버튼: 클릭 시 AiModal 열기

#### 툴바
- 블록 타입 5종 버튼 (숙소 / 식당 / 카페 / 장소 / 이동)
- 클릭 시 해당 타입이 선택된 상태로 BlockModal 열기

#### 보드 (TripBoard)
- Day 컬럼을 가로 스크롤로 나열
- 맨 오른쪽에 "Day 추가" 버튼
  - 클릭 시 마지막 Day 날짜 + 1일로 새 컬럼 추가

#### Day 컬럼 (DayColumn)
- 헤더: "Day N" + 날짜 (예: 3월 10일 (월))
- 헤더 우측 `+` 버튼: 해당 Day에 블록 추가 (BlockModal 열기)
- 블록 카드 사이에 커넥터 선 표시
- 드롭 존: 다른 Day의 블록을 드롭 받을 수 있음
  - 드래그 오버 시 보더를 보라색 점선으로 변경

#### 블록 카드 (BlockCard)
- 구성 요소: 타입 뱃지 / 장소명 / 시간 + 소요시간 + 비용 메타 정보
- 기본 상태: 오른쪽에 드래그 핸들 아이콘 표시
- 호버 상태: 드래그 핸들 숨김 + 편집 버튼 / 삭제 버튼 표시
- 잠금 상태 (`lockedBy != null`):
  - 핑크 테두리 (`#D4537E`, 1.5px)
  - 타입 뱃지 옆에 "OO 편집 중" 잠금 뱃지 표시
  - 호버 시 편집/삭제 버튼 미표시
  - `draggable="false"`, 드래그 이벤트 차단
- 드래그 중 상태: `opacity: 0.35`
- 드래그 타겟 표시: 커서 위치 기준 위/아래에 보라색 2px 선 표시

---

### 2. BlockModal (블록 추가 / 편집)

블록 추가와 편집 모두 동일한 모달을 사용한다.

#### 필드
| 필드 | 입력 타입 | 필수 |
|------|-----------|------|
| 유형 선택 | 타입 버튼 그룹 (단일 선택) | 필수 |
| 장소명 | text input | 필수 |
| 시작 시간 | time input | 선택 |
| 소요 시간 | text input | 선택 |
| 비용 | text input | 선택 |
| 메모 | text input | 선택 |

#### 동작
- 편집 모달 진입 시 기존 값으로 필드 초기화
- 저장 클릭 시:
  - 추가: `POST /api/trips/{tripId}/blocks` 호출 + WebSocket `ADD` 이벤트 발행
  - 편집: `PATCH /api/trips/{tripId}/blocks/{blockId}` 호출 + WebSocket `UPDATE` 이벤트 발행
- 편집 모달 열릴 때 `POST /api/trips/{tripId}/blocks/{blockId}/lock` 호출
- 모달 닫힐 때 (저장 / 취소 / 외부 클릭) `DELETE /api/trips/{tripId}/blocks/{blockId}/lock` 호출

---

### 3. AiModal (AI 일정 생성 — 3단계)

#### 1단계: 옵션 설정
| 필드 | 입력 타입 | 기본값 |
|------|-----------|--------|
| 생성 항목 태그 | 멀티 선택 칩 (전체 / 숙소 / 식당 / 카페 / 장소 / 이동) | 전체 |
| 적용 Day | 멀티 선택 칩 (전체 Day / Day 1 / Day 2 ...) | 전체 Day |
| 여행 스타일 | 단일 선택 드롭다운 (맛집 위주 / 관광 위주 / 쇼핑 위주 / 휴양 위주) | 맛집 위주 |
| 하루 강도 | 단일 선택 드롭다운 (여유롭게 / 보통 / 빡빡하게) | 보통 |
| 추가 요청 사항 | text input | 없음 |

- "전체 일정" 태그 선택 시 나머지 태그 전체 해제
- "전체 Day" 칩 선택 시 나머지 Day 칩 전체 해제
- "생성하기" 클릭 시 `POST /api/trips/{tripId}/ai/generate` 호출 → 2단계로 이동

#### 2단계: 결과 확인 및 Day 배치
- 생성된 블록 목록을 체크박스 리스트로 표시
- 각 행 구성: 체크박스 / 타입 뱃지 / 장소명 / AI 추천 시간 / Day 선택 드롭다운
- 체크 해제 시 해당 행 흐리게 표시 + Day 드롭다운 비활성화
- "전체 선택 / 전체 해제" 버튼
- 하단에 "선택된 N개" 카운트 표시
- "← 다시 설정" 클릭 시 1단계로 돌아감
- "에디터에 추가하기" 클릭 시 `POST /api/trips/{tripId}/ai/apply` 호출 → 3단계로 이동

#### 3단계: 완료
- "N개 블록이 추가됐어요" 완료 메시지
- "확인" 클릭 시 모달 닫기

---

## 드래그 앤 드롭 동작 명세

### 드래그 시작 (dragstart)
- 잠금 상태(`locked`) 블록은 드래그 시작 차단
- 드래그 중인 블록에 `opacity: 0.35` 적용

### 드래그 오버 (dragover)
- 블록 카드 위에서: 커서 Y 위치가 블록 중간보다 위면 `drag-target-above`, 아래면 `drag-target-below` 표시
- Day 컬럼 위에서: 컬럼 테두리 보라색 점선으로 변경

### 드롭 (drop)
- 블록 간 드롭: 타겟 블록 위/아래에 삽입, 새 position 계산
- 컬럼 빈 영역 드롭: 해당 Day 마지막 위치에 추가
- 드롭 후 `PATCH /api/trips/{tripId}/blocks/{blockId}/move` 호출
- WebSocket `MOVE` 이벤트 발행

### 드래그 종료 (dragend)
- 모든 드래그 관련 클래스 초기화

---

## WebSocket 클라이언트 명세

### 연결
```typescript
const client = new Client({
  webSocketFactory: () => new SockJS('/ws'),
  onConnect: () => {
    client.subscribe(`/topic/trip.${tripId}`, onMessage)
  }
})
```

### 수신 이벤트 처리 (onMessage)

| action | 처리 |
|--------|------|
| `ADD` | 해당 dayNumber 블록 목록에 추가, position 기준 정렬 |
| `UPDATE` | blockId 일치 블록 필드 업데이트 |
| `MOVE` | blockId 일치 블록의 dayNumber · position 업데이트 후 전체 정렬 |
| `DELETE` | blockId 일치 블록 목록에서 제거 |
| `LOCK` | blockId 일치 블록의 `lockedBy` · `lockedByNickname` 설정 |
| `UNLOCK` | blockId 일치 블록의 `lockedBy` · `lockedByNickname` null 처리 |
| `CONFLICT` | 해당 블록을 `currentBlock` 으로 롤백, 토스트 알림 표시 |
| `PRESENCE` | 접속자 목록 업데이트 |

### Optimistic Update 처리
1. 사용자 액션 발생 시 로컬 상태 즉시 업데이트 (낙관적 적용)
2. WebSocket 이벤트 발행
3. `CONFLICT` 수신 시 해당 블록을 서버 상태로 롤백
4. 롤백 시 "다른 사용자가 수정했습니다" 토스트 표시

---

## 에러 / 예외 처리

| 상황 | 처리 |
|------|------|
| 잠금 블록 편집 시도 | 편집/삭제 버튼 미노출, 드래그 차단 |
| version 충돌 (`CONFLICT`) | 로컬 상태 롤백 + 토스트 알림 |
| WebSocket 연결 끊김 | 자동 재연결 시도 (STOMP 내장 reconnectDelay 사용) |
| API 에러 | 토스트 알림으로 메시지 표시, 낙관적 업데이트 롤백 |
| 권한 없음 (403) | "편집 권한이 없습니다" 안내 후 VIEWER 모드 전환 |
