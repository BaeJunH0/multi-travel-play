# ai 패키지

OpenAI API를 통해 여행 일정 블록을 AI로 생성하고, 사용자가 선택한 블록을 DB에 저장하는 패키지.

---

## API 엔드포인트

모든 엔드포인트: 인증 필수, 최소 권한 **EDITOR** (VIEWER 접근 시 `403`)

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/api/trips/{tripId}/ai/generate` | AI로 일정 블록 생성, Redis에 10분 보관 |
| `POST` | `/api/trips/{tripId}/ai/apply` | 생성된 블록 중 선택한 항목을 DB에 저장 |

---

## 핵심 비즈니스 로직

### generate 흐름

`@Transactional` 없음 (DB 쓰기 없음)

1. 요청자가 해당 trip의 EDITOR 이상인지 확인 (`requireEditorOrAbove`)
2. trip의 `destination`, `startDate`, `endDate`로 `buildPrompt` 생성
   - `tags` 비어있으면 "전체", `targetDays` 비어있으면 "전체"로 프롬프트에 포함
3. `OpenAiClient.generate(prompt)` → OpenAI `gpt-*` 모델 호출
   - System prompt: 순수 JSON만 반환하도록 지시
   - 응답에서 마크다운 코드블록 제거 후 `blocks` 배열 파싱
4. `UUID.randomUUID()`로 `generationId` 생성
5. 블록 목록을 JSON 직렬화해 Redis에 저장: 키 `ai:generation:{generationId}`, **TTL 10분**
6. `GenerateResponse(generationId, blocks)` 반환 (DB 저장 없음)

### apply 흐름

`@Transactional` 적용. 연산 순서는 아래와 같이 **Redis 체크를 Trip 조회 앞에** 배치한다.
(Redis 만료가 가장 흔한 실패 케이스이므로, 불필요한 DB 조회를 줄이기 위함)

1. 요청자 권한 확인 (`requireEditorOrAbove`) — 인증 우선
2. Redis에서 `ai:generation:{generationId}` 조회 → 없으면 `404` ("생성 결과가 만료되었습니다.")
3. Trip 조회 → 없으면 `404`
4. `selectedBlocks`의 `tempId`로 AI 블록 필터링 → 없는 `tempId`이면 `400`
5. 선택된 블록마다 `GooglePlacesClient.findLatLng(placeName)` 호출 → `lat/lng` 보강
   - Places API 실패 또는 결과 없으면 `null` 허용 (warn 로그 후 계속 진행)
6. `ScheduleBlock` 엔티티 생성 후 `blockRepository.save`
   - `dayNumber`, `position`은 요청의 `SelectedBlock`에서, 나머지 필드는 AI 블록에서 가져옴
7. apply 완료 후 Redis 키 **즉시 삭제** (`redisTemplate.delete`)
8. `ApplyResponse(addedBlocks)` 반환

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
- `GenerateRequest`: `tags: List<BlockType>`, `targetDays: List<Int>`, `style`, `intensity`, `extraRequest?`
- `ApplyRequest`: `generationId: String`, `selectedBlocks: List<SelectedBlock>`
- `SelectedBlock`: `tempId`, `dayNumber`, `position: Double`

**응답**
- `GenerateResponse`: `generationId`, `blocks: List<AiBlock>`
- `AiBlock`: `tempId`, `blockType`, `placeName`, `startTime?`, `durationMin?`, `cost?`, `suggestedDay`, `memo?`
- `ApplyResponse`: `addedBlocks: List<AppliedBlock>`
- `AppliedBlock`: `tempId`, `block: BlockResponse`

---

## 외부 의존성

| 의존성 | 용도 | 설정 키 | 타임아웃 |
|--------|------|---------|---------|
| OpenAI API | `POST /v1/chat/completions` — 일정 블록 생성 | `ai.openai.api-key`, `ai.openai.model` (`OpenAiProperties`) | connect 10s / read **30s** |
| Google Places API | `findplacefromtext` — `placeName`으로 `lat/lng` 조회 | `google.places.api-key` | connect 5s / read 10s |
| Redis | AI 생성 결과 임시 저장 (TTL 10분), apply 후 즉시 삭제 | Spring Boot Redis 공통 설정 | — |

모든 HTTP 클라이언트는 `RestClient` + `JdkClientHttpRequestFactory` 기반 (동기 블로킹).
`spring-boot-starter-webflux` 의존성 없음.

### Redis 키 패턴

```
ai:generation:{generationId}   // 값: AiBlock 목록 JSON, TTL 10분
```

---

## 구현 세부 사항

### OpenAiClient 응답 파싱

OpenAI 응답은 private data class로 타입 안전하게 역직렬화한다.

```
ChatCompletionResponse → choices[0].message.content (String)
  → 마크다운 코드블록 제거 후
BlocksPayload → blocks: List<RawBlock>
  → AiBlock으로 변환
```

### GooglePlacesClient 에러 처리

`runCatching { ... }.onFailure { log.warn(...) }.getOrNull()` 패턴 사용.
Places API 실패 시 warn 로그를 남기고 `null` 반환, 블록 저장은 계속 진행된다.

---

## 주의사항

- `apply`는 트랜잭션 내에서 여러 블록을 순차 저장하므로 하나라도 실패하면 전체 롤백됨
- `apply` 내 Places API 호출은 현재 순차(N번) 방식 — 블록 수가 많아질 경우 Virtual Thread 병렬화 검토 필요
