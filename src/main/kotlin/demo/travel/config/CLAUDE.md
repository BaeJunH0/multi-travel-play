# config 패키지

Spring MVC 및 WebSocket 관련 설정을 담당하는 패키지.

## WebConfig

`WebMvcConfigurer`를 구현해 커스텀 `ArgumentResolver`를 등록한다.

- `CurrentUserArgumentResolver` 등록 — 컨트롤러 메서드 파라미터에서 현재 인증된 사용자를 자동 주입할 때 사용.

## WebSocketConfig

`@EnableWebSocketMessageBroker`로 STOMP 기반 WebSocket을 활성화한다.

| 항목 | 값 |
|---|---|
| STOMP 엔드포인트 | `/ws` (SockJS 폴백 활성화) |
| 메시지 브로커 구독 prefix | `/topic` |
| 클라이언트 → 서버 전송 prefix | `/app` |
