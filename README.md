# 여행 플래너 백엔드

여러 명이 함께 여행 일정을 실시간으로 편집할 수 있는 협업 여행 플래너 서비스의 백엔드입니다.

여행 계획은 보통 여러 사람이 함께 세우지만, 정작 편집은 한 명씩 돌아가며 하거나 메신저로 의견을 취합해 반영하는 경우가 많습니다.
이 프로젝트는 하루 단위 "블록"(숙소/식사/카페/장소/이동)으로 일정을 구성하고, 여러 사용자가 동시에 접속해 실시간으로 같은 일정을 편집·조율할 수 있게 하는 것을 목표로 합니다.

## 주요 기능

- **실시간 협업 편집**: WebSocket(STOMP)으로 블록 추가/수정/이동/삭제가 접속 중인 모든 멤버에게 즉시 브로드캐스트됩니다. 접속자 presence(활성 Day 포함)도 함께 공유됩니다.
- **블록 기반 일정 관리**: Day별로 블록을 자유롭게 드래그 앤 드롭으로 재배치할 수 있으며, `position` 값 기반으로 순서를 관리하고 필요 시 재정규화합니다.
- **동시 편집 충돌 방지**: 블록 편집 시 잠금(lock)을 걸어 다른 사용자의 동시 수정을 막고, `version` 기반 낙관적 잠금으로 갱신 충돌을 감지해 `409 VERSION_CONFLICT`로 알려줍니다.
- **AI 일정 자동 생성**: OpenAI API로 목적지·기간·스타일·강도 등 조건에 맞는 일정 초안을 생성하고, 사용자가 선택한 블록만 골라 적용할 수 있습니다. Google Places API로 장소 좌표를 보강합니다.
- **인증/인가**: 이메일 회원가입·로그인 외에 카카오/구글 소셜 로그인을 지원하며, JWT(access) + Redis 기반 refresh token, 이메일 인증 코드 기반 비밀번호 재설정을 제공합니다.
- **권한 관리**: 여행별로 OWNER/EDITOR/VIEWER 역할을 두어 조회·편집·삭제 권한을 구분합니다.
- **초대 링크**: 공유 토큰 기반 초대 링크로 VIEWER 권한 멤버를 손쉽게 추가할 수 있습니다.
- **예산 관리**: 여행별 예산 항목(카테고리, 금액, 정산자)을 등록하고 관리합니다.

## 기술 스택

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)
![OpenAI](https://img.shields.io/badge/OpenAI-412991?style=for-the-badge&logo=openai&logoColor=white)
![Google](https://img.shields.io/badge/Google-4285F4?style=for-the-badge&logo=google&logoColor=white)
![Kakao](https://img.shields.io/badge/Kakao-FFCD00?style=for-the-badge&logo=kakaotalk&logoColor=black)

## 기여자

<table>
  <tr>
    <td align="center">
      <a href="https://github.com/BaeJunH0">
        <img src="https://github.com/BaeJunH0.png" width="100" alt="BaeJunH0"/><br />
        <sub><b>BaeJunH0</b></sub>
      </a>
    </td>
  </tr>
</table>

## 실행 방법

```bash
# MySQL, Redis 실행 후
./gradlew bootRun
```

DB 테이블은 `src/main/resources/schema.sql` 참고. `schema.sql`은 `CREATE TABLE IF NOT EXISTS`라 기존 DB에는 반영되지 않으므로, 이미 테이블이 있는 환경이라면 `src/main/resources/migration/`의 스크립트를 날짜순으로 직접 실행해야 한다.

## 문서

API/DB/AI 프롬프트/테스트 문서는 [docs/](docs/) 디렉터리를 참고하세요.
