export type BlockType = 'HOTEL' | 'FOOD' | 'CAFE' | 'PLACE' | 'TRANSPORT'
export type TripRole = 'OWNER' | 'EDITOR' | 'VIEWER'
export type BudgetCategory = 'HOTEL' | 'FOOD' | 'CAFE' | 'PLACE' | 'TRANSPORT' | 'FLIGHT' | 'ETC'

export interface Block {
  id: string
  dayNumber: number
  position: number          // float (1.0, 1.5, 2.0 ...)
  blockType: BlockType
  placeName: string
  lat: number | null
  lng: number | null
  startTime: string | null  // "HH:mm"
  durationMin: number | null
  cost: number | null
  memo: string | null
  lockedBy: string | null
  lockedByNickname: string | null
  version: number
}

export interface Trip {
  id: string
  title: string
  destination: string
  startDate: string
  endDate: string
  memberCount: number
  myRole: TripRole
}

export interface TripDetail extends Trip {
  days: TripDay[]
}

export interface TripDay {
  dayNumber: number
  date: string
}

export interface Presence {
  userId: string
  nickname: string
  avatarColor: string
  activeDay?: number | null
}

export interface User {
  id: string
  email: string
  nickname: string
  provider: string
}

export interface TripMember {
  userId: string
  nickname: string
  role: TripRole
}

export interface BudgetItem {
  id: string
  blockId: string | null
  category: BudgetCategory
  amount: number
  memo: string | null
}

// AI
export interface AiGeneratedBlock {
  tempId: string
  blockType: BlockType
  placeName: string
  startTime: string | null
  durationMin: number | null
  cost: number | null
  suggestedDay: number
  memo: string | null
}

export interface AiGenerateResponse {
  blocks: AiGeneratedBlock[]
}

// SSE 실시간 이벤트
// action별 payload 형식. action으로 switch하면 payload 타입이 좁혀진다
export interface TripEventPayloads {
  ADD: Block
  UPDATE: { blockId: string; block: Block }
  MOVE: { blockId: string; dayNumber: number; position: number; version: number }
  DELETE: { blockId: string }
  LOCK: { blockId: string; lockedBy: string | null; lockedByNickname: string | null }
  UNLOCK: { blockId: string }
  CONFLICT: { blockId: string; currentBlock: Block }
  PRESENCE: { users: Presence[] }
}

export type TripEventAction = keyof TripEventPayloads

export type TripEvent = {
  [A in TripEventAction]: { action: A; tripId: string; payload: TripEventPayloads[A] }
}[TripEventAction]

export const BLOCK_STYLE: Record<BlockType, { bg: string; border: string; badge: string; label: string }> = {
  HOTEL:     { bg: '#E1F5EE', border: '#1D9E75', badge: '#9FE1CB', label: '숙소' },
  FOOD:      { bg: '#FAECE7', border: '#D85A30', badge: '#F5C4B3', label: '식당' },
  CAFE:      { bg: '#FAEEDA', border: '#BA7517', badge: '#FAC775', label: '카페' },
  PLACE:     { bg: '#EEEDFE', border: '#7F77DD', badge: '#CECBF6', label: '장소' },
  TRANSPORT: { bg: '#E6F1FB', border: '#378ADD', badge: '#B5D4F4', label: '이동' },
}
