import api from './api'
import type { Block, BlockType } from '../types'

export const blockApi = {
  getBlocks: (tripId: string) =>
    api.get<Block[]>(`/trips/${tripId}/blocks`),

  addBlock: (tripId: string, data: {
    dayNumber: number
    blockType: BlockType
    placeName: string
    startTime: string | null
    durationMin: number | null
    cost: number | null
    memo: string | null
  }) => api.post<Block>(`/trips/${tripId}/blocks`, data),

  updateBlock: (tripId: string, blockId: string, data: Partial<Block> & { version: number }) =>
    api.patch<Block>(`/trips/${tripId}/blocks/${blockId}`, data),

  moveBlock: (tripId: string, blockId: string, data: { dayNumber: number; position: number; version: number }) =>
    api.patch<Block>(`/trips/${tripId}/blocks/${blockId}/move`, data),

  deleteBlock: (tripId: string, blockId: string) =>
    api.delete(`/trips/${tripId}/blocks/${blockId}`),

  lockBlock: (tripId: string, blockId: string) =>
    api.post<Block>(`/trips/${tripId}/blocks/${blockId}/lock`),

  unlockBlock: (tripId: string, blockId: string) =>
    api.delete(`/trips/${tripId}/blocks/${blockId}/lock`),

  // position이 바뀐 블록만 돌려준다 (version도 올라가 있다)
  reorder: (tripId: string) =>
    api.post<Block[]>(`/trips/${tripId}/blocks/reorder`),
}
