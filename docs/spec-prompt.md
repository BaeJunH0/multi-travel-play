# 여행 플래너 — AI 프롬프트 명세

## System Prompt
```
당신은 여행 일정 전문가입니다.
사용자의 조건에 맞는 여행 일정 블록을 생성하고,
반드시 아래 JSON 형식만 반환하세요.
마크다운 코드블록, 설명 텍스트 없이 순수 JSON만 출력하세요.
```

## User Prompt 템플릿
```
목적지: {destination}
기간: {startDate} ~ {endDate} ({totalDays}일)
생성 항목: {tags}
적용 Day: {targetDays}
여행 스타일: {style}
하루 강도: {intensity}
추가 요청: {extraRequest}

반환 형식:
{
  "blocks": [
    {
      "tempId": "temp-{n}",
      "blockType": "FOOD | CAFE | PLACE | HOTEL | TRANSPORT",
      "placeName": "장소명",
      "startTime": "HH:mm",
      "durationMin": 60,
      "cost": 0,
      "suggestedDay": 1,
      "memo": "메모 또는 null"
    }
  ]
}
```

## 요청 설정
- 모델 `claude-sonnet-5`, `max_tokens` 16000 (adaptive thinking이 출력과 한도를 나눠 씀), read timeout 120초, 비스트리밍
- 호출 실패: 429·5xx·529·시간 초과·연결 실패는 `503 AI_UNAVAILABLE`, 그 외 4xx는 `502 AI_REQUEST_FAILED` (Anthropic 에러 본문은 서버 로그). 자동 재시도 없음

## 응답 파싱 처리
1. `stop_reason`이 `end_turn`인지 확인 — 아니면(예: `max_tokens`로 잘림) 실패
2. `content`에서 `type`이 `text`인 첫 블록을 고름 (adaptive thinking이 켜져 있으면 `thinking` 블록이 먼저 올 수 있음)
3. 텍스트에서 마크다운 코드블록 표시(```` ```json ````, ```` ``` ````)를 벗겨내고 JSON 파싱
   - 1~3단계 중 하나라도 실패하면 `502 AI_RESPONSE_INVALID`
4. `tempId` 기준으로 클라이언트 응답에 포함해 반환
5. `placeName` 으로 Google Places API 조회 → `lat` / `lng` 보정 후 DB 저장 (`/ai/apply` 단계)
