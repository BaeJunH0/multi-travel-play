import { useEffect, useRef } from 'react'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { auth } from '../store/auth'
import { authApi } from '../services/authApi'
import { tripApi } from '../services/tripApi'
import type { TripEvent } from '../types'

interface Options {
  tripId: string
  activeDay: number | null
  onMessage: (msg: TripEvent) => void
}

const RETRY_DELAY_MS = 3000

// 재시도하지 않을 응답 (403 · 404 등)
class FatalError extends Error {}

// 활성 Day를 서버에 알린다. SSE 연결 전이면 서버가 무시한다
function sendPresence(tripId: string, activeDay: number | null) {
  tripApi.updatePresence(tripId, activeDay).catch(() => { /* 다음 변경 때 다시 보낸다 */ })
}

export function useTripEvents({ tripId, activeDay, onMessage }: Options) {
  const onMessageRef = useRef(onMessage)
  const activeDayRef = useRef(activeDay)

  useEffect(() => {
    onMessageRef.current = onMessage
  })

  useEffect(() => {
    const ctrl = new AbortController()

    fetchEventSource(`/api/trips/${tripId}/events`, {
      signal: ctrl.signal,
      // 탭이 숨겨져도 연결을 유지한다 (마지막 연결이 끊기면 서버가 내 잠금을 해제하므로)
      openWhenHidden: true,
      // 재연결할 때마다 최신 accessToken을 붙인다
      fetch: (input, init) => {
        const headers = new Headers(init?.headers)
        headers.set('Authorization', `Bearer ${auth.getToken() ?? ''}`)
        return fetch(input, { ...init, headers })
      },
      async onopen(res) {
        if (res.ok) {
          sendPresence(tripId, activeDayRef.current)
          return
        }
        // accessToken 만료: api 인터셉터의 refresh 흐름을 타도록 아무 인증 API나 호출한 뒤 재연결
        if (res.status === 401) {
          await authApi.me()
          throw new Error('unauthorized')
        }
        throw new FatalError(`SSE 연결 실패: ${res.status}`)
      },
      onmessage(ev) {
        if (!ev.data) return
        try {
          onMessageRef.current(JSON.parse(ev.data))
        } catch { /* ignore malformed */ }
      },
      onclose() {
        // 서버가 스트림을 닫으면(재시작 등) 다시 연결한다
        throw new Error('closed')
      },
      onerror(err) {
        if (err instanceof FatalError) throw err
        return RETRY_DELAY_MS
      },
    }).catch(() => { /* FatalError: 재연결 중단 */ })

    return () => ctrl.abort()
  }, [tripId])

  // 활성 Day가 바뀌면 Presence 갱신
  useEffect(() => {
    activeDayRef.current = activeDay
    sendPresence(tripId, activeDay)
  }, [tripId, activeDay])
}
