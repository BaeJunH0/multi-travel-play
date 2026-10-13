# UI 컴포넌트

## 컴포넌트 트리

```
App
├── LoginPage
├── TripListPage
│   └── TripCard (내부 컴포넌트)
├── TripEditorPage
│   ├── AvatarStack
│   ├── DayColumn (days 배열만큼 반복)
│   │   └── BlockCard (blocks 배열만큼 반복)
│   │       └── BlockTypeBadge
│   ├── BlockModal
│   │   └── BlockTypeBadge
│   └── AiModal
│       └── BlockTypeBadge
└── InvitePage
```

---

## 페이지 컴포넌트

### LoginPage (`/login`)

**상태:**
- `tab: 'social' | 'email'` — 소셜 / 이메일 탭 전환
- `loading: boolean` — 로그인 요청 중 버튼 비활성화

**Props:**
```typescript
interface Props {
  onLogin: () => void  // 로그인 성공 시 App의 initState를 'authed'로 변경
}
```

**구성:**
- 소셜 탭: 카카오 버튼 → `window.location.href = '/api/auth/oauth2/kakao'`
- 이메일 탭: email/password input, Eye 토글, 로그인 버튼
- 탭 전환은 라우팅 없이 `useState`로 처리

---

### TripListPage (`/`)

**상태:**
- `trips: Trip[]` — 여행 목록
- `loading: boolean` — 초기 로딩

**구성:**
- 헤더: 로고 + 로그아웃 버튼
- 그리드: `grid-cols-1 sm:grid-cols-2 lg:grid-cols-3`으로 반응형 배치
- TripCard: 여행 제목, 목적지, 날짜/박수, 인원수, 역할 뱃지

**역할 뱃지 색상:**

| Role | 배경 | 텍스트 |
|---|---|---|
| OWNER | `bg-violet-100` | `text-violet-600` |
| EDITOR | `bg-blue-100` | `text-blue-600` |
| VIEWER | `bg-gray-100` | `text-gray-500` |

---

### TripEditorPage (`/trips/:tripId`)

**상태:**
- `trip: TripDetail | null` — 여행 정보 + days
- `presence: Presence[]` — 실시간 접속자
- `blockModalOpen / aiModalOpen: boolean` — 모달 열림 상태
- `editingBlock: Block | null` — 편집 중인 블록 (null이면 추가 모드)
- `draggedBlockId: string | null` — 드래그 중인 블록 ID
- `dragTargetInfo: { blockId, pos } | null` — 드롭 대상 위치

**핵심 훅:**
- `useTripBlocks(tripId, [])` — 블록 상태 + CRUD + WS 메시지 처리
- `useWebSocket(...)` — STOMP 연결 + PRESENCE 분기 처리

**TopBar 구성:**
- `←` 뒤로 가기 (여행 목록으로)
- 여행 제목
- `N명 편집 중` + 초록 점 (presence.length > 0 일 때)
- AvatarStack (접속자 아바타)
- 공유 버튼 (초대 링크 클립보드 복사) — VIEWER 숨김
- AI 생성 버튼 — VIEWER 숨김

**Toolbar:**
- 블록 타입 5종 버튼 (VIEWER 숨김)
- 클릭 시 해당 타입으로 BlockModal 오픈

**Board:**
- Day 컬럼 가로 스크롤 (`overflow-x-auto`)
- 맨 오른쪽 "+" 버튼으로 Day 추가 (endDate + 1일)

---

### InvitePage (`/invite/:shareToken`)

**상태:**
- `info: InviteInfo | null` — 초대 정보
- `status: 'loading' | 'pending' | 'accepted' | 'error'`

**흐름:**
1. `loading` → 초대 정보 API 호출
2. `pending` → 여행 정보 표시 + "참여하기" 버튼
3. `accepted` → CheckCircle 아이콘 + "에디터로 이동 중..." → 1.2초 후 리다이렉트
4. `error` → "유효하지 않은 초대 링크예요" 메시지

---

## 블록 컴포넌트

### DayColumn

```typescript
interface Props {
  day: TripDay
  blocks: Block[]
  isViewer?: boolean
  canDelete?: boolean
  draggedBlockId: string | null
  dragTargetInfo: { blockId: string; pos: 'above' | 'below' } | null
  onAddBlock: (dayNumber: number, type?: BlockType) => void
  onDeleteDay: (dayNumber: number) => void
  onChangeDate: (dayNumber: number, newDate: string) => void
  onEditBlock: (block: Block) => void
  onDeleteBlock: (blockId: string) => void
  // DnD 핸들러들 ...
}
```

**구성:**
- 고정 너비 `w-64`, 흰 배경 카드 (`rounded-2xl shadow-sm`)
- 헤더: "Day N" + 날짜 (클릭 시 date input으로 인라인 편집)
- blocks를 `position` 오름차순으로 정렬 후 렌더링
- 블록 사이에 연결선 (`w-px h-3 bg-gray-200`)
- 드래그 오버 시 보라색 점선 테두리 (`2px dashed #7F77DD`)

**날짜 인라인 편집:**
- 날짜 텍스트 클릭 → `<input type="date">` 표시
- `onChange` 또는 `onBlur` 시 `onChangeDate` 호출

---

### BlockCard

```typescript
interface Props {
  block: Block
  onEdit: (block: Block) => void
  onDelete: (blockId: string) => void
  isViewer?: boolean
  isDragging?: boolean
  dragTargetPos?: 'above' | 'below' | null
  // DnD 이벤트 핸들러들 ...
}
```

**상태별 스타일:**

| 상태 | 스타일 |
|---|---|
| 기본 | blockType 배경색 + 좌측 3px 컬러 보더 |
| 잠금 (`lockedBy != null`) | 핑크 테두리 `#D4537E 1.5px`, "OO 편집 중" 뱃지 |
| 드래그 중 (`isDragging`) | `opacity: 0.35` |
| 드롭 타겟 (above) | 카드 위에 `h-0.5 bg-violet-500` 라인 |
| 드롭 타겟 (below) | 카드 아래에 `h-0.5 bg-violet-500` 라인 |

**표시 필드:**
- BlockTypeBadge
- 잠금 뱃지 (잠금 상태일 때)
- 장소명 (`font-semibold truncate`)
- 시작 시간 (Clock 아이콘)
- 소요 시간 (Timer 아이콘, 단위: 분)
- 비용 (Coins 아이콘, `toLocaleString()원`)
- 메모 (`text-xs text-gray-400 truncate`)
- 삭제 버튼 (Trash2 아이콘, editable일 때만)

**editable 조건:** `!isViewer && !locked`  
editable이 false이면 클릭(편집), 드래그, 삭제 버튼이 모두 비활성화됩니다.

---

## 모달 컴포넌트

### BlockModal

```typescript
interface Props {
  open: boolean
  initialType?: BlockType
  initialDayNumber?: number
  editBlock?: Block | null    // null이면 추가 모드
  onClose: () => void
  onSave: (data: ...) => void
}
```

**필드:**

| 필드 | 입력 타입 | 필수 여부 |
|---|---|---|
| 유형 | 타입 버튼 그룹 | ✅ |
| 장소명 | text | ✅ |
| 시작 시간 | time | 선택 |
| 소요 시간(분) | number | 선택 |
| 비용(원) | number | 선택 |
| 메모 | text | 선택 |

**동작:**
- `editBlock`이 있으면 기존 값으로 초기화 (편집 모드)
- 없으면 `initialType` + 빈 필드 (추가 모드)
- 배경 클릭 또는 Escape 키로 닫기
- `placeName`이 비어 있으면 저장 버튼 비활성화

---

### AiModal

3단계 스텝 위저드로 구성됩니다.

```typescript
interface Props {
  open: boolean
  tripId: string
  days: TripDay[]
  onClose: () => void
  onApply: (blocks: Array<AiGeneratedBlock & { dayNumber: number }>) => void
}
```

**단계별 구성:**

**Step 1 — 옵션 설정**

| 필드 | 컨트롤 | 기본값 |
|---|---|---|
| 생성 항목 | 멀티 선택 칩 (전체/숙소/식당/카페/장소/이동) | 전체 |
| 여행 스타일 | select | 맛집 위주 |
| 하루 강도 | select | 보통 |
| 추가 요청 | text input | 없음 |

"전체" 칩 선택 시 개별 타입 선택 해제.  
"생성하기" 클릭 → `POST /ai/generate` → Step 2로 이동

**Step 2 — 결과 확인 및 Day 배치**
- 체크박스 목록으로 생성된 블록 표시
- 체크 해제 시 행 `opacity-40` + Day 드롭다운 비활성화
- 각 행에서 Day 드롭다운으로 배치 Day 변경 가능
- 전체 선택 / 전체 해제 버튼
- "선택된 N개" 카운트
- "← 다시 설정" → Step 1로 복귀
- "에디터에 추가하기" → `POST /ai/apply` → Step 3

**Step 3 — 완료**
- "N개 블록이 추가됐어요" 메시지
- "확인" 클릭 → 모달 닫기

---

## UI 공통 컴포넌트

### BlockTypeBadge

```typescript
interface Props {
  type: BlockType
  size?: 'sm' | 'md'  // 기본값: 'sm'
}
```

`BLOCK_STYLE` 상수에서 `badge` 배경색과 `border` 텍스트색을 가져와 렌더링합니다.

### AvatarStack

```typescript
interface Props {
  presence: Presence[]
  max?: number  // 기본값: 3
}
```

- 최대 `max`개의 아바타를 `-space-x-2`로 겹쳐 표시
- 초과 인원은 "+N" 회색 원으로 표시
- 각 아바타: `avatarColor` 배경, 닉네임 첫 글자, `title`에 전체 닉네임

---

## 블록 타입 스타일 상수

```typescript
const BLOCK_STYLE: Record<BlockType, { bg, border, badge, label }> = {
  HOTEL:     { bg: '#E1F5EE', border: '#1D9E75', badge: '#9FE1CB', label: '숙소' },
  FOOD:      { bg: '#FAECE7', border: '#D85A30', badge: '#F5C4B3', label: '식당' },
  CAFE:      { bg: '#FAEEDA', border: '#BA7517', badge: '#FAC775', label: '카페' },
  PLACE:     { bg: '#EEEDFE', border: '#7F77DD', badge: '#CECBF6', label: '장소' },
  TRANSPORT: { bg: '#E6F1FB', border: '#378ADD', badge: '#B5D4F4', label: '이동' },
}
```

`bg`: 카드 배경 / `border`: 좌측 포인트 컬러 + 텍스트 색 / `badge`: 뱃지 배경

---

## 드래그 앤 드롭

HTML5 Drag and Drop API를 사용하며, 별도 훅 없이 `TripEditorPage`에서 직접 관리합니다.

### 이벤트 흐름

```
dragStart (BlockCard)
  └─ setDraggedBlockId → isDragging=true → opacity 0.35

dragOver (BlockCard)
  └─ 커서 Y 위치 기준 위/아래 계산
      └─ setDragTargetInfo → 보라색 2px 라인 표시

dragOver (DayColumn)
  └─ setIsDragOver → 보라색 점선 테두리

drop (BlockCard)
  └─ calcPosition(sorted, targetIdx, pos)
      └─ moveBlock(srcId, target.dayNumber, newPosition)

drop (DayColumn 빈 영역)
  └─ newPosition = max(기존 positions) + 1.0
      └─ moveBlock(srcId, dayNumber, newPosition)

dragEnd
  └─ 모든 drag 상태 초기화
```

### position 계산 (`calcPosition`)

```
타겟 위에 삽입 (above):
  prev가 있으면 → (prev.position + target.position) / 2
  없으면       → target.position - 1.0

타겟 아래 삽입 (below):
  next가 있으면 → (target.position + next.position) / 2
  없으면       → target.position + 1.0
```

position gap이 `1e-4` 미만으로 수렴하면 `POST /blocks/reorder`로 재정규화를 요청합니다.

### 잠금 블록 DnD 차단

```typescript
draggable={editable}          // editable = !locked && !isViewer
```

`draggable` 속성을 false로 설정해 브라우저 레벨에서 드래그를 차단합니다.
