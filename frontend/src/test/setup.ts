import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

// vitest 전역(globals)을 쓰지 않으므로 Testing Library의 자동 정리가 등록되지 않는다. 테스트마다 렌더링한 루트를 직접 정리한다.
afterEach(() => cleanup())
