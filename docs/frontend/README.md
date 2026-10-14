# TripPlanner Frontend — 문서 인덱스

함께 만드는 실시간 여행 일정 플래너의 프론트엔드입니다. 소스는 [`frontend/`](../../frontend/)에 있습니다.

## 기술 스택

| 영역 | 기술 |
|---|---|
| Language | TypeScript |
| Framework | React 19 |
| Routing | React Router v7 |
| Styling | Tailwind CSS v4 |
| HTTP | Axios |
| 실시간 | @stomp/stompjs + SockJS |
| 상태 관리 | React 내장 (useState / useCallback / Context 없이 Props drilling) |
| DnD | HTML5 Drag and Drop API |
| Toast | react-hot-toast |

## 화면 목록

| 경로 | 화면 | 설명 |
|---|---|---|
| `/login` | 로그인 | 카카오/구글 OAuth · 이메일 로그인 · 회원가입 · 비밀번호 재설정 |
| `/` | 여행 목록 | 내 여행 카드 그리드 |
| `/trips/:tripId` | 블록 에디터 | 메인 편집 보드 (실시간) |
| `/invite/:shareToken` | 초대 수락 | 공유 링크 진입 후 여행 참여 |

## 디렉토리 구조

```
src/
├── pages/          # 라우트 단위 페이지 컴포넌트
├── components/     # 재사용 UI 컴포넌트
│   ├── modals/     # BlockModal, AiModal, MemberModal, BudgetModal
│   └── ui/         # BlockTypeBadge, AvatarStack
├── hooks/          # useWebSocket, useTripBlocks
├── services/       # API 모듈 (axios)
├── store/          # accessToken · 현재 유저 메모리 스토어
├── types/          # 타입 정의 + BLOCK_STYLE 상수
└── mock/           # 프로토타입 시절 목 데이터 (현재 어디서도 import하지 않음)
```

## 문서 목록

| 파일 | 내용 |
|---|---|
| [intro.md](./intro.md) | 화면별 기능 소개 |
| [auth.md](./auth.md) | 토큰 전략, 로그인/로그아웃, 라우팅 가드 |
| [spec-api.md](./spec-api.md) | REST API 엔드포인트 전체 정리 (원본은 [docs/api/](../api/)) |
| [spec-frontend.md](./spec-frontend.md) | 화면 · 컴포넌트 · 동작 명세 |
| [websocket.md](./websocket.md) | STOMP 연결, 이벤트 처리, Optimistic Update |
| [ui.md](./ui.md) | 컴포넌트 구조, Props, 스타일 규칙 |
| [user-flows.md](./user-flows.md) | 주요 시나리오별 사용자 플로우 |
