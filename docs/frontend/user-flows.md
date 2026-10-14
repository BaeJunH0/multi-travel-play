# 유저 플로우

## 1. 첫 진입 / 인증 복구

```
브라우저에서 앱 접속
  └─ App 마운트
       └─ POST /api/auth/refresh (refreshToken 쿠키 전송)
            ├─ 성공
            │   └─ accessToken 메모리 저장
            │       └─ GET /api/auth/me → 현재 유저 메모리 저장 (Presence · 내 잠금 판별에 사용)
            │           └─ "/" → 여행 목록 페이지
            └─ 실패
                └─ "/login" → 로그인 페이지
```

새로고침 후에도 HttpOnly 쿠키가 남아있으면 로그인 상태가 자동으로 복구됩니다.

---

## 2. 로그인

### 카카오 / 구글 소셜 로그인

```
로그인 페이지 → "카카오로 계속하기" 또는 "구글로 계속하기" 클릭
  └─ window.location.href = '/api/auth/oauth2/kakao' (구글: '/api/auth/oauth2/google')
       └─ 카카오/구글 인증 서버 → 서버 OAuth 콜백
            └─ 서버가 refreshToken 쿠키 설정 후 프론트엔드로 리다이렉트
                 └─ App 마운트 → refresh 복구 → 여행 목록
```

### 이메일 로그인

```
로그인 페이지 → "이메일로 로그인" 클릭 (탭 전환)
  └─ 이메일 / 비밀번호 입력 → 로그인 버튼
       └─ POST /api/auth/login
            ├─ 성공
            │   └─ accessToken 저장 → GET /api/auth/me → "/" → 여행 목록
            └─ 실패 (401)
                └─ "이메일 또는 비밀번호가 올바르지 않습니다" 토스트
```

### 회원가입

```
이메일 로그인 탭 → "회원가입" 클릭
  └─ 이메일 / 비밀번호(8자 이상) / 비밀번호 확인 / 닉네임 입력 → 가입하기
       └─ POST /api/auth/signup
            ├─ 성공 → accessToken 저장 → "/" → 여행 목록
            └─ 실패 (409) → "이미 사용 중인 이메일입니다" 토스트
```

### 비밀번호 재설정

```
이메일 로그인 탭 → "비밀번호 찾기" 클릭
  └─ [1단계] 이메일 입력 → "인증코드 전송"
       └─ POST /api/auth/password-reset/request
            └─ 성공/실패와 무관하게 2단계로 이동 (가입 여부 비노출)
  └─ [2단계] 인증코드(숫자 6자리) + 새 비밀번호 입력 → "비밀번호 변경"
       └─ POST /api/auth/password-reset/confirm
            ├─ 성공 → accessToken 저장 → "/" → 여행 목록
            └─ 실패 (400) → "인증 코드가 올바르지 않거나 만료됐습니다" 토스트
```

---

## 3. 여행 목록

```
여행 목록 페이지 마운트
  └─ GET /api/trips
       └─ 여행 카드 그리드 렌더링
            ├─ 카드 클릭 → "/trips/:tripId" (에디터)
            └─ "새 여행 만들기" 클릭 → 모달 (제목 · 목적지 · 시작일 · 종료일)
                 └─ POST /api/trips → "/trips/:newTripId" (에디터)

로그아웃 버튼 클릭
  └─ POST /api/auth/logout
       └─ auth.clearToken() → "/login"
```

---

## 4. 블록 에디터 진입

```
"/trips/:tripId" 진입
  └─ 병렬 로드
       ├─ GET /api/trips/:tripId  → trip + days
       └─ GET /api/trips/:tripId/blocks → 블록 목록
            └─ 로딩 완료 → 보드 렌더링

  └─ useWebSocket 연결 (STOMP)
       └─ Presence 발행 { userId, nickname, avatarColor, activeDay }
            └─ PRESENCE 수신 → 접속자 목록 → TopBar 업데이트
  └─ 활성 Day가 바뀌면 Presence 재발행
  └─ 다른 접속자가 탭을 닫거나 연결이 끊기면 서버가 PRESENCE를 다시 브로드캐스트
```

---

## 5. 블록 추가

### Toolbar에서 추가

```
Toolbar 타입 버튼 클릭 (예: "식당")
  └─ 해당 타입이 선택된 상태로 BlockModal 열기
       └─ 장소명 입력 + 선택 필드 작성
            └─ "저장" 클릭
                 └─ POST /api/trips/:tripId/blocks
                      └─ 응답 Block → 로컬 상태에 추가 → 모달 닫기
```

### Day 컬럼 "+" 버튼에서 추가

```
Day 헤더의 "+" 클릭
  └─ 타입 미지정으로 BlockModal 열기 (기본 타입: PLACE)
       └─ (이후 동일)
```

---

## 6. 블록 편집

```
블록 카드 클릭 (잠금 해제 상태, EDITOR/OWNER)
  └─ POST /api/trips/:tripId/blocks/:blockId/lock
       ├─ 성공
       │   └─ 편집 모달 열기 (기존 값으로 필드 초기화)
       │        └─ 수정 후 "저장" 클릭
       │             └─ PATCH /api/trips/:tripId/blocks/:blockId (version 포함)
       │                  ├─ 성공 → 서버 응답으로 상태 확정 → unlock → 모달 닫기
       │                  └─ 409 → 응답의 currentBlock(서버 최신)으로 교체 → 토스트 → unlock → 모달 닫기
       │
       │        (모달이 열린 동안 내 블록 카드에는 "내가 편집 중" 배지 표시)
       │
       │        └─ "취소" 또는 배경/Escape 클릭
       │             └─ DELETE /blocks/:blockId/lock → 모달 닫기
       │
       └─ 실패 (423 ALREADY_LOCKED)
           └─ "OO님이 편집 중입니다." 토스트 → 모달 미열림

타인이 잠근 블록은 카드 클릭 자체가 비활성이다 (핑크 테두리 + "OO 편집 중" 배지).
```

---

## 7. 블록 삭제

```
블록 카드의 삭제(Trash2) 아이콘 클릭
  └─ 로컬 상태에서 즉시 제거 (낙관적)
       └─ DELETE /api/trips/:tripId/blocks/:blockId
            ├─ 성공 → 완료
            ├─ 실패 (423, 다른 사용자가 잠금 중) → 로컬 상태 복구 + "OO님이 편집 중입니다." 토스트
            └─ 그 외 실패 → 로컬 상태 복구 + "삭제에 실패했습니다" 토스트
```

---

## 8. 드래그 앤 드롭으로 블록 이동

### 같은 Day 내 순서 변경

```
블록 드래그 시작
  └─ opacity 0.35 적용

다른 블록 위에 드래그 오버
  └─ 커서 위치에 따라 위/아래 보라색 라인 표시

드롭
  └─ position 계산 (두 인접 블록의 중간값)
       └─ 로컬 상태 즉시 반영 (낙관적)
            └─ PATCH /blocks/:blockId/move
                 ├─ 성공 → gap < 1e-4이면 POST /blocks/reorder
                 └─ 실패 (423, 다른 사용자가 잠금 중) → 이전 위치로 복구 + "OO님이 편집 중입니다." 토스트
```

### 다른 Day로 이동

```
블록을 다른 Day 컬럼의 빈 영역에 드롭
  └─ dayNumber 변경 + 해당 Day 마지막 position으로 배치
       └─ PATCH /blocks/:blockId/move (dayNumber, position 모두 변경)
```

---

## 9. Day 관리

### Day 추가

```
보드 맨 오른쪽 "+" 버튼 클릭
  └─ PATCH /trips/:tripId { endDate: 현재 endDate + 1일 }
       └─ 서버 응답 TripDetail의 days 배열로 화면 갱신
```

### Day 삭제

```
Day 헤더의 삭제(Trash2) 버튼 클릭
  ├─ 해당 Day의 블록 전체 삭제 (Promise.all)
  └─ 마지막 Day라면
  │    └─ PATCH /trips/:tripId { endDate: 현재 endDate - 1일 }
  └─ 중간 Day라면
       └─ 이후 Day 블록의 dayNumber를 -1씩 조정 (moveBlock 일괄 호출)
            └─ PATCH /trips/:tripId { endDate: 현재 endDate - 1일 }
```

### 날짜 변경

```
Day 날짜 텍스트 클릭 → date input 표시
  └─ 날짜 선택 후 포커스 해제
       └─ Day 1이면 startDate 변경
          Day N(마지막)이면 endDate 변경
          중간이면 전체 범위를 delta만큼 이동
               └─ PATCH /trips/:tripId { startDate, endDate }
```

---

## 10. 여행 공유 (초대)

### 초대 링크 생성 (OWNER/EDITOR)

```
TopBar "공유" 버튼 클릭
  └─ POST /api/trips/:tripId/invite
       └─ 응답의 inviteUrl을 클립보드에 복사
            └─ "초대 링크가 복사됐어요!" 토스트
```

### 초대 수락 (수신자)

```
공유된 링크 접속 (/invite/:shareToken)
  └─ GET /api/invite/:shareToken (인증 불필요)
       ├─ 성공 → 여행 정보 표시 + "참여하기" 버튼
       │    └─ "참여하기" 클릭
       │         └─ POST /api/invite/:shareToken/accept
       │              └─ "참여 완료!" 표시 → 1.2초 후 에디터로 이동
       └─ 실패 → "유효하지 않은 초대 링크예요" 표시
```

---

## 11. AI 일정 생성

```
TopBar "AI 생성" 버튼 클릭
  └─ AiModal 열기 (Step 1)

[Step 1] 옵션 설정
  ├─ 생성 항목 선택 (전체 또는 개별 타입)
  ├─ 여행 스타일 선택
  ├─ 하루 강도 선택
  ├─ 추가 요청 사항 입력 (선택)
  └─ "생성하기" 클릭
       └─ POST /api/trips/:tripId/ai/generate
            ├─ 성공 → Step 2
            └─ 실패 → 서버 에러 메시지 토스트 (예: 503 "잠시 후 다시 시도", 502 응답 해석 실패). 메시지가 없으면 "AI 생성에 실패했습니다"

[Step 2] 결과 확인 및 Day 배치
  ├─ 체크박스로 원하는 블록 선택/해제
  ├─ 각 블록의 배치 Day를 드롭다운으로 변경 가능
  ├─ "← 다시 설정" → Step 1 (기존 생성 결과 유지)
  └─ "에디터에 추가하기" (선택된 개수 > 0)
       └─ 각 Day의 현재 최대 position 다음부터 1.0씩 position 부여
       └─ POST /api/trips/:tripId/ai/apply
            ├─ 성공 → Step 3 + 로컬 블록 상태에 추가
            └─ 실패 → "일정 적용에 실패했습니다" 토스트

[Step 3] 완료
  └─ "N개 블록이 추가됐어요" 표시
       └─ "확인" 클릭 → 모달 닫기
```

---

## 12. 실시간 동시 편집

```
사용자 A가 블록 수정
  └─ A: 낙관적으로 로컬 상태 즉시 업데이트
       └─ REST PATCH 호출
            └─ 서버: WebSocket UPDATE 브로드캐스트
                 └─ 사용자 B: UPDATE 수신 → 블록 상태 업데이트

두 사용자가 같은 블록을 동시에 수정하는 경우
  └─ A가 먼저 저장 성공 (version: 1 → 2)
  └─ B가 version: 1로 저장 시도
       └─ 서버: B에게 409 VERSION_CONFLICT(currentBlock 포함) 반환 + CONFLICT 브로드캐스트
            ├─ B: 409 응답의 currentBlock으로 교체 → "다른 사용자가 수정했습니다" 토스트 (1회)
            └─ 다른 접속자: CONFLICT 수신 → currentBlock으로 동기화 (토스트 없음)
```
