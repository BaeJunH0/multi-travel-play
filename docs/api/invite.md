> 공통 사항(Base URL, 에러 응답 형식 등)은 [README.md](./README.md) 참고

# 초대 (Invite)

### 초대 링크 생성
```
POST /api/trips/{tripId}/invite
```
**응답**
```json
{ "shareToken": "string", "inviteUrl": "string" }
```
- 이미 토큰이 있으면 기존 값을 그대로 사용 (재호출해도 덮어쓰지 않음)
- 최소 권한: EDITOR

### 초대 정보 조회
```
GET /api/invite/{shareToken}
```
**응답**
```json
{
  "tripId": "uuid",
  "tripTitle": "string",
  "destination": "string",
  "startDate": "YYYY-MM-DD",
  "endDate": "YYYY-MM-DD",
  "memberCount": 3,
  "inviterNickname": "string"
}
```
- 인증 불필요

### 초대 수락
```
POST /api/invite/{shareToken}/accept
```
**응답** `201 Created`
```json
{ "tripId": "uuid" }
```
- VIEWER 권한으로 trip_members 등록
- 이미 멤버인 경우 tripId만 반환 (멱등)
