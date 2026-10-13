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

// WebSocket
export type WsAction = 'ADD' | 'UPDATE' | 'MOVE' | 'DELETE' | 'LOCK' | 'UNLOCK' | 'CONFLICT' | 'PRESENCE'

export interface WsMessage {
  action: WsAction
  tripId: string
  payload: WsPayload
}

export type WsPayload =
  | { block: Block }                                                          // ADD
  | { blockId: string; block: Block }                                        // UPDATE
  | { blockId: string; dayNumber: number; position: number; version: number }// MOVE
  | { blockId: string }                                                       // DELETE
  | { blockId: string; lockedBy: string; lockedByNickname: string }          // LOCK
  | { blockId: string }                                                       // UNLOCK
  | { blockId: string; currentBlock: Block }                                 // CONFLICT
  | { users: Presence[] }                                                    // PRESENCE

export const BLOCK_STYLE: Record<BlockType, { bg: string; border: string; badge: string; label: string }> = {
  HOTEL:     { bg: '#E1F5EE', border: '#1D9E75', badge: '#9FE1CB', label: '숙소' },
  FOOD:      { bg: '#FAECE7', border: '#D85A30', badge: '#F5C4B3', label: '식당' },
  CAFE:      { bg: '#FAEEDA', border: '#BA7517', badge: '#FAC775', label: '카페' },
  PLACE:     { bg: '#EEEDFE', border: '#7F77DD', badge: '#CECBF6', label: '장소' },
  TRANSPORT: { bg: '#E6F1FB', border: '#378ADD', badge: '#B5D4F4', label: '이동' },
}
