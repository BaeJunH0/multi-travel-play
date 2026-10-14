# 여행 플래너 프론트엔드

여러 명이 함께 여행 일정을 실시간으로 편집할 수 있는 협업 여행 플래너 서비스의 프론트엔드입니다.

## 기술 스택

| 영역 | 기술 |
|------|------|
| Language / Framework | TypeScript ~6.0 + React 19 |
| 스타일 | Tailwind CSS v4 (`@tailwindcss/vite`) |
| 빌드 | Vite v8 |
| HTTP | Axios v1 (공통 인스턴스 + 인터셉터) |
| 라우팅 | react-router-dom v7 |
| 실시간 | @stomp/stompjs v7 + SockJS |
| 알림 | react-hot-toast |
| 아이콘 | lucide-react |

## 프로젝트 구조

```
src/
├── main.tsx
├── App.tsx                       # 라우터 설정, OAuth 콜백 처리
│
├── types/
│   └── index.ts                  # 공통 타입 (Block, Trip, TripDetail 등)
│
├── store/
│   └── auth.ts                   # accessToken 인메모리 저장소
│
├── pages/
│   ├── LoginPage.tsx             # 이메일 로그인/회원가입, 카카오/구글 OAuth
│   ├── TripListPage.tsx          # 내 여행 목록, 새 여행 생성 모달
│   ├── TripEditorPage.tsx        # 여행 편집기 (블록 DnD, 실시간 협업)
│   └── InvitePage.tsx            # 초대 링크 수락 페이지
│
├── components/
│   ├── BlockCard.tsx             # 개별 블록 카드 (잠금 상태 표시 포함)
│   ├── DayColumn.tsx             # Day 컬럼 (드래그 앤 드롭 컨테이너)
│   ├── modals/
│   │   ├── BlockModal.tsx        # 블록 추가/편집 모달
│   │   ├── AiModal.tsx           # AI 일정 생성 모달
│   │   ├── MemberModal.tsx       # 멤버 목록 · 역할 변경 · 내보내기
│   │   └── BudgetModal.tsx       # 예산 항목 관리
│   └── ui/
│       ├── BlockTypeBadge.tsx    # 블록 타입 뱃지
│       └── AvatarStack.tsx       # 접속자 아바타 스택
│
├── hooks/
│   ├── useTripBlocks.ts          # 블록 상태 관리 + WebSocket 이벤트 처리
│   └── useWebSocket.ts           # STOMP 연결 + Presence 발행
│
├── services/
│   ├── api.ts                    # Axios 인스턴스, 401 인터셉터
│   ├── authApi.ts                # 인증 API (로그인, 회원가입, OAuth, 비밀번호 재설정)
│   ├── tripApi.ts                # 여행 CRUD
│   ├── blockApi.ts               # 블록 CRUD + 잠금
│   ├── inviteApi.ts              # 초대 링크
│   ├── memberApi.ts              # 멤버 조회 · 역할 변경 · 제거
│   ├── budgetApi.ts              # 예산 CRUD
│   └── aiApi.ts                  # AI 일정 생성/적용
│
└── mock/
    └── data.ts                   # 프로토타입 시절 목 데이터 (현재 import하는 곳 없음)
```

## 인증 흐름

- **accessToken**: 인메모리 저장 (`src/store/auth.ts`) — XSS 방어
- **refreshToken**: HttpOnly 쿠키로 서버 관리 (7일 TTL)
- `api.ts` 요청 인터셉터: `Authorization: Bearer {accessToken}` 자동 주입
- 401 응답 시: `POST /api/auth/refresh` → 재발급 성공 시 원 요청 재시도, 실패 시 로그인 페이지 이동
- 소셜 로그인(카카오/구글): 서버 URL로 브라우저 리다이렉트 → OAuth 완료 후 `/` 로 복귀

## 실시간 협업 (WebSocket)

- `useWebSocket`: STOMP over SockJS로 `/ws` 연결, `/topic/trip.{tripId}` 구독
- `useTripBlocks`: Optimistic Update 적용 후 WebSocket 이벤트로 최종 상태 동기화
- version 충돌(`409 VERSION_CONFLICT`) 시 응답의 `currentBlock`으로 교체하고 토스트를 한 번 띄운다. 다른 접속자는 `CONFLICT` 이벤트로 같은 블록을 동기화한다
- 블록 잠금은 `lockedBy`를 현재 유저 id와 비교해 "내가 편집 중" / "OO 편집 중"으로 구분한다. 잠금 · 이동 · 삭제가 `423 ALREADY_LOCKED`면 "OO님이 편집 중입니다." 토스트를 띄운다
- Presence는 연결 시와 활성 Day 변경 시 `{ userId, nickname, avatarColor, activeDay }`로 발행한다

| 이벤트 | 처리 |
|--------|------|
| `ADD` | 블록 목록에 추가 |
| `UPDATE` | 해당 블록 필드 업데이트 |
| `MOVE` | dayNumber, position, version 갱신 |
| `DELETE` | 블록 목록에서 제거 |
| `LOCK` / `UNLOCK` | lockedBy, lockedByNickname 갱신 |
| `CONFLICT` | 해당 블록을 `currentBlock`으로 교체 (토스트는 409 받은 요청자만) |
| `PRESENCE` | 접속자 목록 갱신 |

## 페이지 및 라우트

| 경로 | 컴포넌트 | 설명 |
|------|----------|------|
| `/login` | `LoginPage` | 이메일 로그인/회원가입, 카카오/구글 소셜 로그인, 비밀번호 재설정 |
| `/` | `TripListPage` | 내 여행 목록, 새 여행 생성 |
| `/trips/:tripId` | `TripEditorPage` | 여행 편집기 (실시간 협업) |
| `/invite/:shareToken` | `InvitePage` | 초대 수락 |

## 참고

- 비밀번호 재설정 UI는 구현되어 있지만, 백엔드에 SMTP 환경변수(`MAIL_*`)가 없으면 인증 코드 메일이 발송되지 않는다.

## 로컬 실행

```bash
# 백엔드 서버(8080) 실행 후
npm install
npm run dev
# → http://localhost:5173
```

Vite dev 서버가 `/api` → `localhost:8080`, `/ws` → `localhost:8080`(WebSocket)으로 프록시합니다.

## 환경 변수

별도 `.env` 불필요. API URL은 Vite 프록시로 처리합니다.
