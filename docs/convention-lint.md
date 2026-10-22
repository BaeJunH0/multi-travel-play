# 린트·검증 컨벤션

코드를 올리기 전에 지켜야 할 스타일·타입 규칙과, 그것을 확인하는 명령을 정리한다.
아래 명령은 PR과 `main` 푸시마다 GitHub Actions([`.github/workflows/ci.yml`](../.github/workflows/ci.yml))가 똑같이 돌린다.

---

## 검증 명령

| 대상 | 위치 | 명령 | 검사 내용 |
|------|------|------|-----------|
| 백엔드 | `backend/` | `./gradlew check` | ktlint(main·test·빌드 스크립트) + 전체 테스트(ArchUnit 아키텍처 테스트 포함) |
| 프론트 | `frontend/` | `npm ci` | lock 파일 그대로 깨끗하게 설치 (lock이 `package.json`과 어긋나면 실패) |
| 프론트 | `frontend/` | `npm run lint` | eslint |
| 프론트 | `frontend/` | `npm run typecheck` | TypeScript 타입 검사 (`tsc -b`) |
| 프론트 | `frontend/` | `npm test` | Vitest 테스트 (`vitest run`). 작성 규칙은 [테스트 명세 8절](spec-test.md#8-프론트-테스트) |

PR을 올리기 전에 로컬에서 바뀐 쪽 명령을 돌려 본다. CI의 두 job(`backend`, `frontend`)이 모두 통과해야 머지한다.

---

## 백엔드: ktlint

- 플러그인: `org.jlleitschuh.gradle.ktlint` ([`backend/build.gradle.kts`](../backend/build.gradle.kts)). `check`에 `ktlintCheck`가 포함된다.
- 설정: 저장소 루트 [`.editorconfig`](../.editorconfig)
  - `ktlint_code_style = intellij_idea` — IntelliJ 기본 포맷과 가까운 스타일
  - `max_line_length = 140`
  - 와일드카드 import 금지(`standard:no-wildcard-imports`). 기본 허용 패키지인 `java.util.*`만 예외다.
    IntelliJ가 import를 `*`로 합치지 않도록 `.editorconfig`에 star import 기준을 최대로 두었다.
- 끈 규칙은 없다. 규칙을 끄거나 완화해야 하면 `.editorconfig`에 사유 주석과 함께 적고 PR에서 따로 설명한다.

### 위반을 고치는 법

```bash
cd backend
./gradlew ktlintFormat   # 자동 수정 가능한 위반을 고친다
./gradlew ktlintCheck    # 남은 위반 확인 (리포트: build/reports/ktlint/)
```

자동으로 안 고쳐지는 위반(와일드카드 import, 긴 줄 등)은 손으로 고친다. 대량 포맷은 기능 변경과 다른 커밋으로 나눈다.

---

## 백엔드: 아키텍처 테스트

패키지·계층 규칙은 ArchUnit 테스트(`backend/src/test/kotlin/demo/travel/architecture/ArchitectureTest.kt`)가 검사하고
`./gradlew check`의 테스트 단계에서 함께 돈다. 규칙 내용은 [DTO 배치 컨벤션](convention-dto.md)을 참고한다.

---

## 프론트: eslint·타입 검사

- eslint 설정: [`frontend/eslint.config.js`](../frontend/eslint.config.js) — `@eslint/js` recommended, `typescript-eslint` recommended,
  `react-hooks` recommended, `react-refresh`(vite)
  - `any`를 쓰지 않는다(`@typescript-eslint/no-explicit-any`). 오류 객체는 `isAxiosError`로 좁히고, 서버 이벤트처럼 형식이 여러 개인 값은 유니온 타입으로 표현한다.
  - `useEffect`·`useCallback` 의존성 배열을 빠뜨리지 않는다(`react-hooks/exhaustive-deps`).
- 타입 검사: `tsconfig.app.json`의 Linting 옵션(`noUnusedLocals`, `noUnusedParameters`, `noFallthroughCasesInSwitch` 등)을 따른다.
- 의존성을 바꾸면 `npm install`로 `package-lock.json`도 함께 갱신해 커밋한다. CI는 `npm ci`로 설치하므로 lock이 어긋나면 실패한다.
