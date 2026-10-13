import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs.min.js'
import { auth } from '../store/auth'
import type { WsMessage } from '../types'

interface Options {
  tripId: string
  activeDay: number | null
  onMessage: (msg: WsMessage) => void
}

const AVATAR_COLORS = ['#7F77DD', '#E8735A', '#3BA889', '#E0A63B', '#4A90D9', '#D45D9A']

// userId 기반으로 항상 같은 아바타 색을 고른다
function avatarColorOf(userId: string) {
  let hash = 0
  for (const ch of userId) hash = (hash * 31 + ch.charCodeAt(0)) | 0
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length]
}

// Presence 알림 (백엔드 PresenceRequest 형식)
function publishPresence(client: Client, tripId: string, activeDay: number | null) {
  const user = auth.getUser()
  if (!user || !client.connected) return
  client.publish({
    destination: `/app/trip.${tripId}.presence`,
    body: JSON.stringify({
      userId: user.id,
      nickname: user.nickname,
      avatarColor: avatarColorOf(user.id),
      activeDay,
    }),
  })
}

export function useWebSocket({ tripId, activeDay, onMessage }: Options) {
  const clientRef = useRef<Client | null>(null)
  const onMessageRef = useRef(onMessage)
  onMessageRef.current = onMessage
  const activeDayRef = useRef(activeDay)

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: { Authorization: `Bearer ${auth.getToken() ?? ''}` },
      reconnectDelay: 3000,
      onConnect: () => {
        client.subscribe(`/topic/trip.${tripId}`, (frame) => {
          try {
            const msg: WsMessage = JSON.parse(frame.body)
            onMessageRef.current(msg)
          } catch { /* ignore malformed */ }
        })
        publishPresence(client, tripId, activeDayRef.current)
      },
    })

    client.activate()
    clientRef.current = client

    return () => { client.deactivate() }
  }, [tripId])

  // 활성 Day가 바뀌면 Presence 재전송
  useEffect(() => {
    activeDayRef.current = activeDay
    if (clientRef.current) publishPresence(clientRef.current, tripId, activeDay)
  }, [tripId, activeDay])
}
