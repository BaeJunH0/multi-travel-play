import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs.min.js'
import { auth } from '../store/auth'
import type { WsMessage } from '../types'

interface Options {
  tripId: string
  onMessage: (msg: WsMessage) => void
}

export function useWebSocket({ tripId, onMessage }: Options) {
  const clientRef = useRef<Client | null>(null)
  const onMessageRef = useRef(onMessage)
  onMessageRef.current = onMessage

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
        // Presence 알림
        client.publish({ destination: `/app/trip.${tripId}.presence` })
      },
    })

    client.activate()
    clientRef.current = client

    return () => { client.deactivate() }
  }, [tripId])
}
