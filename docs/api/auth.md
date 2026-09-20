> 공통 사항(Base URL, 에러 응답 형식 등)은 [README.md](./README.md) 참고

# 인증 (Auth)

### 카카오 소셜 로그인 시작
```
GET /api/auth/oauth2/kakao
```
- 카카오 인증 URL로 `302 redirect`
- 인증 불필요

### 카카오 OAuth 콜백
```
GET /api/auth/oauth2/kakao/callback?code={code}
```
- 카카오로부터 전달받은 code로 토큰 교환 및 유저 upsert
- 성공 시 refreshToken 쿠키(HttpOnly) 설정 후 `/` 로 redirect
- 인증 불필요

### 구글 소셜 로그인 시작
```
GET /api/auth/oauth2/google
```
- 구글 인증 URL로 `302 redirect`
- 인증 불필요

### 구글 OAuth 콜백
```
GET /api/auth/oauth2/google/callback?code={code}
```
- 구글로부터 전달받은 code로 토큰 교환 및 유저 upsert
- 성공 시 refreshToken 쿠키(HttpOnly) 설정 후 `/` 로 redirect
- 인증 불필요

### 이메일 회원가입
```
POST /api/auth/signup
```
**요청**
```json
{ "email": "string", "password": "string", "nickname": "string" }
```
**응답** `201 Created`
```json
{ "accessToken": "string" }
```
**에러**: 이메일 중복 시 `409 CONFLICT`
- 인증 불필요

### 이메일 로그인
```
POST /api/auth/login
```
**요청**
```json
{ "email": "string", "password": "string" }
```
**응답** + `Set-Cookie: refreshToken=...; HttpOnly; Path=/api/auth`
```json
{ "accessToken": "string" }
```
**에러**: 이메일/비밀번호 불일치 시 `401 UNAUTHORIZED`
- 인증 불필요

### 토큰 갱신
```
POST /api/auth/refresh
```
- 쿠키의 `refreshToken` 으로 새 accessToken 발급
- **응답**
```json
{ "accessToken": "string" }
```
**에러**: refreshToken 없거나 만료 시 `401 UNAUTHORIZED`
- 인증 불필요

### 내 정보 조회
```
GET /api/auth/me
```
**응답**
```json
{ "id": "uuid", "email": "string", "nickname": "string", "provider": "LOCAL | KAKAO | GOOGLE" }
```

### 로그아웃
```
POST /api/auth/logout
```
**응답** `204 No Content`
- Redis에서 refreshToken 삭제 + refreshToken 쿠키 만료
- 인증 불필요

### 비밀번호 재설정 코드 발송
```
POST /api/auth/password-reset/request
```
**요청**
```json
{ "email": "string" }
```
**응답** `200 OK`
- 이메일로 6자리 인증 코드 발송, Redis에 15분간 보관
- 소셜 로그인 계정(Kakao/Google) 미지원
- 인증 불필요

### 비밀번호 재설정 확인
```
POST /api/auth/password-reset/confirm
```
**요청**
```json
{ "email": "string", "token": "string (6자리 코드)", "newPassword": "string (8자 이상)" }
```
**응답**
```json
{ "accessToken": "string" }
```
**에러**: 코드 불일치/만료 시 `400 INVALID_RESET_TOKEN`
- 인증 불필요
