# block 패키지

여행 일정 블록(ScheduleBlock)의 CRUD, 순서 관리, 잠금, 실시간 이벤트 발행을 담당한다.

---

## API 엔드포인트

모든 엔드포인트는 `Authorization: Bearer {accessToken}` 필요. 최소 권한이 EDITOR인 경우 VIEWER는 403.

| Method | Path | 설명 | 최소 권한 |
|--------|------|------|----------|
| GET | `/api/trips/{tripId}/blocks` | 블록 목록 조회 (day·position 오름차순) | VIEWER |
| POST | `/api/trips/{tripId}/blocks` | 블록 추가 (position 자동: 해당 day 마지막 + 1.0) | EDITOR |
| PATCH | `/api/trips/{tripId}/blocks/{blockId}` | 블록 수정 (낙관적 잠금 — `version` 필수) | EDITOR |
| PATCH | `/api/trips/{tripId}/blocks/{blockId}/move` | 블록 이동 DnD (`dayNumber`, `position`, `version` 필수) | EDITOR |
| DELETE | `/api/trips/{tripId}/blocks/{blockId}` | 블록 삭제 | EDITOR |
| POST | `/api/trips/{tripId}/blocks/reorder` | position 재정규화 (1.0, 2.0, 3.0 ...) | EDITOR |
| POST | `/api/trips/{tripId}/blocks/{blockId}/lock` | 잠금 획득 (편집 모달 열릴 때) | EDITOR |
| DELETE | `/api/trips/{tripId}/blocks/{blockId}/lock` | 잠금 해제 (편집 모달 닫힐 때) | 본인 잠금만 |

---

## 핵심 비즈니스 로직

### Fractional Indexing
블록 순서를 `position: Double` 소수로 관리해 이동 시 DB 업데이트를 1건으로 유지한다.

- 맨 뒤 추가: `lastPosition + 1.0` (신규 추가 시 항상 이 방식 사용)
- 두 블록 사이 삽입: `(prev + next) / 2` — 클라이언트가 계산해서 전송
- 맨 앞 삽입: `prevPosition - 1.0`
- `position` 간격이 `1e-9` 미만으로 좁아지면 클라이언트가 `/reorder`를 호출해야 한다. `reorderBlocks()`는 day별로 `1.0, 2.0, 3.0 ...`으로 재정규화한다.

### 낙관적 잠금 (Optimistic Lock)
`ScheduleBlock.version`은 JPA `@Version` 필드다. `updateBlock`, `moveBlock` 시 요청의 `version`이 현재 엔티티와 다르면 `VersionConflictException`을 던진다. 이 예외는 `409 VERSION_CONFLICT` 응답과 함께 현재 서버 상태의 블록 객체를 반환한다.

```
checkVersion(block, command.version)  // version 불일치 → VersionConflictException(현재 BlockResult)
```

### 블록 잠금 (Block Lock)
`lockedBy: User?` 필드로 관리한다.

- `lockBlock`: `lockedBy`가 다른 사용자이면 `423 ALREADY_LOCKED`. 본인이거나 null이면 자신으로 설정.
- `unlockBlock`: `lockedBy`가 본인일 때만 null로 해제 (다른 사용자 잠금은 건드리지 않음).
- `updateBlock`은 잠금 체크(`checkLock`)도 수행한다 — 다른 사용자가 잠근 블록은 수정 불가.
- `moveBlock`은 잠금 체크 없이 version 체크만 수행한다.

---

## ScheduleBlock 엔티티 주요 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | UUID | PK, 생성 시 자동 발급 |
| `trip` | Trip (ManyToOne, LAZY) | 소속 여행 |
| `dayNumber` | Int | 여행 일차 (1, 2, 3 ...) |
| `position` | Double | Fractional Indexing 순서값 |
| `blockType` | BlockType | `HOTEL / FOOD / CAFE / PLACE / TRANSPORT` |
| `placeName` | String | 장소명 (필수) |
| `lat`, `lng` | Double? | 좌표 (AI 일정 적용 시 Google Places로 보강, null 허용) |
| `startTime` | LocalTime? | 방문 시작 시각 |
| `durationMin` | Int? | 체류 시간(분) |
| `cost` | Int? | 비용 |
| `memo` | String? | 메모 (TEXT) |
| `version` | Long | 낙관적 잠금용 — JPA `@Version` 자동 증가 |
| `lockedBy` | User? (ManyToOne, LAZY) | 잠금 보유 사용자. null이면 잠금 해제 상태 |
| `createdBy` | User (ManyToOne, LAZY) | 블록 생성자 |
| `createdAt` | LocalDateTime | 생성 시각 (불변) |
| `updatedAt` | LocalDateTime | `@PreUpdate`로 자동 갱신 |

---

## WebSocket 이벤트 발행 시점

`BlockController`가 서비스 호출 후 `.also { eventPublisher.publish(...) }` 패턴으로 발행한다. 서비스 로직 성공 후에만 발행된다.

| 컨트롤러 메서드 | TripEvent | 발행 payload |
|--------------|-----------|-------------|
| `addBlock` | `TripEvent.add` | Block 객체 전체 |
| `updateBlock` | `TripEvent.update` | `{ blockId, block }` |
| `moveBlock` | `TripEvent.move` | `{ blockId, dayNumber, position, version }` |
| `deleteBlock` | `TripEvent.delete` | `{ blockId }` |
| `lockBlock` | `TripEvent.lock` | `{ blockId, lockedBy, lockedByNickname }` |
| `unlockBlock` | `TripEvent.unlock` | `{ blockId }` |

`reorder`는 WebSocket 이벤트를 발행하지 않는다.

구독 destination: `/topic/trip.{tripId}`
