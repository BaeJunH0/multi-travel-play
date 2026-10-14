# 인증 (Auth)

## 토큰 전략

이 프로젝트는 **이중 토큰** 방식을 사용합니다.

| 토큰 | 저장 위치 | 목적 |
|---|---|---|
| `accessToken` | **메모리** (JS 변수) | API 요청 인증 |
| `refreshToken` | **HttpOnly 쿠키** (서버 설정) | accessToken 재발급 |

accessToken을 `localStorage` / `sessionStorage`에 저장하지 않는 이유는 XSS 공격으로 탈취될 수 있기 때문입니다. 메모리에만 두면 페이지 새로고침 시 소멸하지만, 앱 진입 시점에 refresh 엔드포인트를 호출해 복구합니다.

### 인증 스토어 (`src/store/auth.ts`)

```typescript
let _accessToken: string | null = null
let _user: User | null = null

export const auth = {
  getToken:   () => _accessToken,
  setToken:   (token: string) => { _accessToken = token },
  clearToken: () => { _accessToken = null; _user = null },
  getUser:    () => _user,
  setUser:    (user: User) => { _user = user },
}
```

모듈 스코프 변수로 관리하므로 어떤 컴포넌트에서든 import해서 사용 가능합니다.
현재 유저(`_user`)는 "내 잠금" 판별(`lockedBy === auth.getUser()?.id`)에 사용합니다.

---

## 앱 진입 시 인증 복구

`App.tsx`는 마운트 시 refreshToken 쿠키로 accessToken 복구를 시도합니다.

```
앱 마운트
  └─ authApi.refresh() 호출 (POST /api/auth/refresh)
        ├─ 성공 → auth.setToken() → GET /api/auth/me → auth.setUser() → initState: 'authed'
        └─ 실패 → initState: 'unauthed'
```

복구 완료 전까지 로딩 스피너(`✈️ animate-pulse`)를 표시합니다.

---

## 로그인 방식

### 카카오 / 구글 OAuth

```typescript
kakaoLogin: () => {
  window.location.href = '/api/auth/oauth2/kakao'
},
googleLogin: () => {
  window.location.href = '/api/auth/oauth2/google'
},
```

서버가 카카오/구글 OAuth 흐름을 처리하고 완료 후 프론트엔드로 리다이렉트합니다.
리다이렉트 후 앱이 다시 마운트되면서 refresh 쿠키로 토큰을 복구합니다.

### 이메일 로그인

```
POST /api/auth/login  { email, password }
  └─ 응답: { accessToken }
       └─ auth.setToken() 호출 → onLogin() 콜백(GET /api/auth/me로 유저 저장) → navigate('/')
```

오류 처리:
- `401` → "이메일 또는 비밀번호가 올바르지 않습니다."
- 그 외 → "로그인에 실패했습니다."

---

## 토큰 자동 갱신 (Axios Interceptor)

`src/services/api.ts`의 응답 인터셉터가 401 응답을 감지해 자동으로 refresh를 시도합니다.

### 흐름

```
API 요청
  └─ 401 응답
       ├─ (이미 갱신 중이면) 대기 큐에 등록
       └─ (최초 401이면)
            └─ POST /api/auth/refresh
                 ├─ 성공 → 새 accessToken 저장 → 큐 일괄 재시도 → 원래 요청 재시도
                 └─ 실패 → auth.clearToken() → window.location.href = '/login'
```

### 대기 큐 (동시 다중 요청 처리)

401이 동시에 여러 요청에서 발생할 때 refresh를 중복 호출하지 않도록 큐잉합니다.

```typescript
let refreshing = false
let queue: Array<(token: string) => void> = []
```

- `refreshing === true`인 동안 들어오는 401 요청은 `queue`에 적재됩니다.
- refresh 성공 시 queue의 모든 콜백에 새 토큰을 전달하고 재시도합니다.

---

## 라우팅 가드

`App.tsx`에서 `initState`에 따라 라우팅을 분기합니다.

```
initState === 'authed'
  ├─ /             → TripListPage
  ├─ /trips/:id    → TripEditorPage
  └─ *             → Navigate to /

initState === 'unauthed'
  └─ *             → Navigate to /login

(공통, 인증 불필요)
  ├─ /login        → LoginPage
  └─ /invite/:token → InvitePage  ← 비로그인 상태에서도 접근 가능
```

`/invite/:shareToken`은 인증 여부와 무관하게 항상 접근 가능합니다. 초대 수락(`acceptInvite`) 자체는 인증된 API 토큰이 필요하므로, 비로그인 사용자가 수락하려 할 경우 서버에서 401을 반환합니다.

---

## 로그아웃

```typescript
const handleLogout = async () => {
  await authApi.logout().catch(() => {})  // 서버 쿠키 삭제
  auth.clearToken()                        // 메모리 토큰 삭제
  navigate('/login')
}
```

`logout` API가 실패해도 로컬 토큰을 지우고 로그인 페이지로 이동합니다.

---

## 권한 모델 (TripRole)

| Role | 설명 | 편집 가능 |
|---|---|---|
| `OWNER` | 여행 생성자 | ✅ |
| `EDITOR` | 초대받은 편집자 | ✅ |
| `VIEWER` | 읽기 전용 참여자 | ❌ |

에디터 페이지에서 `isViewer = (trip.myRole === 'VIEWER')` 플래그로 Toolbar, 편집 버튼, 공유 버튼을 숨깁니다.
