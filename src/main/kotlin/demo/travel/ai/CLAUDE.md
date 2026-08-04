# ai 패키지

Anthropic Claude API를 통해 여행 일정 블록을 AI로 생성하고, 사용자가 선택한 블록을 DB에 저장하는 패키지.

---

## API 엔드포인트

모든 엔드포인트: 인증 필수, 최소 권한 **EDITOR** (VIEWER 접근 시 `403`)

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/api/trips/{tripId}/ai/generate` | AI로 일정 블록 생성 (DB 저장 없음, 프론트에 그대로 반환) |
| `POST` | `/api/trips/{tripId}/ai/apply` | 프론트가 선택한 블록의 내용을 그대로 받아 DB에 저장 |

---

## 핵심 비즈니스 로직

서버는 `generate`와 `apply` 사이에 아무 상태도 들고 있지 않는다(stateless).
`generate` 응답을 프론트가 들고 있다가, 사용자가 고른 블록의 실제 내용을 `apply`
요청에 그대로 실어 보내는 구조.

### generate 흐름

`@Transactional` 없음 (DB 쓰기 없음)

1. 요청자가 해당 trip의 EDITOR 이상인지 확인 (`requireEditorOrAbove`)
2. trip의 `destination`, `startDate`, `endDate`로 `buildPrompt` 생성
   - `tags` 비어있으면 "전체", `targetDays` 비어있으면 "전체"로 프롬프트에 포함
3. `AnthropicClient.generate(prompt)` → Anthropic `claude-*` 모델 호출
   - System prompt: 순수 JSON만 반환하도록 지시
   - 응답에서 마크다운 코드블록 제거 후 `blocks` 배열 파싱
4. `GenerateResponse(blocks)` 반환 (서버에 아무것도 저장하지 않음)

### apply 흐름

`@Transactional` 적용.

1. 요청자 권한 확인 (`requireEditorOrAbove`)
2. Trip 조회 → 없으면 `404`
3. `selectedBlocks` 각 항목마다 `GooglePlacesClient.findLatLng(placeName)` 호출 → `lat/lng` 보강
   - Places API 실패 또는 결과 없으면 `null` 허용 (warn 로그 후 계속 진행)
4. `ScheduleBlock` 엔티티 생성 후 `blockRepository.save`
   - 모든 필드(`blockType`, `placeName`, `startTime` 등)와 `dayNumber`/`position`을
     요청의 `SelectedBlock`에서 그대로 가져옴 — 서버가 별도로 검증/보강하는 원본은 없음
   - 저장된 블록에 cost가 있으면 `BlockCostChangedEvent`를 발행해 budget에도 반영한다 (block/CLAUDE.md "budget 동기화" 참고)
5. `ApplyResponse(addedBlocks)` 반환 (`tempId`는 요청 값을 그대로 echo)

### 권한 체크

```kotlin
private fun requireEditorOrAbove(tripId: UUID, userId: UUID) {
    // 멤버 아닌 경우 → 403
    // TripRole.VIEWER → 403
}
```

---

## DTO 구조

**요청**
- `GenerateRequest`: `tags: List<TripCategory>`, `targetDays: List<Int>`, `style`, `intensity`, `extraRequest?`
- `ApplyRequest`: `selectedBlocks: List<SelectedBlock>`
- `SelectedBlock`: `tempId`, `blockType`, `placeName`, `startTime?`, `durationMin?`, `cost?`, `memo?`, `dayNumber`, `position: Double`

**응답**
- `GenerateResponse`: `blocks: List<AiBlock>`
- `AiBlock`: `tempId`, `blockType`, `placeName`, `startTime?`, `durationMin?`, `cost?`, `suggestedDay`, `memo?`
- `ApplyResponse`: `addedBlocks: List<AppliedBlock>`
- `AppliedBlock`: `tempId`, `block: BlockResponse`

---

## 외부 의존성

| 의존성 | 용도 | 설정 키 | 타임아웃 |
|--------|------|---------|---------|
| Anthropic Claude API | `POST /v1/messages` — 일정 블록 생성 | `ai.anthropic.api-key`, `ai.anthropic.model` (`AnthropicProperties`) | connect 10s / read **30s** |
| Google Places API | `findplacefromtext` — `placeName`으로 `lat/lng` 조회 | `google.places.api-key` | connect 5s / read 10s |

모든 HTTP 클라이언트는 `RestClient` + `JdkClientHttpRequestFactory` 기반 (동기 블로킹).
`spring-boot-starter-webflux` 의존성 없음.

---

## 구현 세부 사항

### AnthropicClient 응답 파싱

Anthropic 응답은 private data class로 타입 안전하게 역직렬화한다.

```
MessagesResponse → content[0].text (String)
  → 마크다운 코드블록 제거 후
BlocksPayload → blocks: List<RawBlock>
  → AiBlock으로 변환
```

### GooglePlacesClient 에러 처리

`runCatching { ... }.onFailure { log.warn(...) }.getOrNull()` 패턴 사용.
Places API 실패 시 warn 로그를 남기고 `null` 반환, 블록 저장은 계속 진행된다.

---

## 주의사항

- `apply`는 요청으로 받은 블록 내용을 서버가 재검증하지 않고 그대로 저장한다.
  이는 새로운 노출이 아니다 — `POST /api/trips/{tripId}/blocks`로 EDITOR는 이미
  임의 내용의 블록을 직접 만들 수 있으므로, "AI가 생성한 내용만 저장되게 강제"하는
  보호는 원래도 없었다.
- `apply`는 트랜잭션 내에서 여러 블록을 순차 저장하므로 하나라도 실패하면 전체 롤백됨
- `apply` 내 Places API 호출은 현재 순차(N번) 방식 — 블록 수가 많아질 경우 Virtual Thread 병렬화 검토 필요
