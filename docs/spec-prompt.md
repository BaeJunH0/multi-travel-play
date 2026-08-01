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

## 응답 파싱 처리
1. Claude 응답 텍스트에서 JSON 추출
2. `tempId` 기준으로 클라이언트 응답에 포함해 반환
3. `placeName` 으로 Google Places API 조회 → `lat` / `lng` 보정 후 DB 저장 (`/ai/apply` 단계)
