import api from './api'
import type { AiGenerateResponse, BlockType } from '../types'

export const aiApi = {
  generate: (tripId: string, data: {
    tags: BlockType[]
    targetDays: number[]
    style: string
    intensity: string
    extraRequest: string | null
  }) => api.post<AiGenerateResponse>(`/trips/${tripId}/ai/generate`, data),

  apply: (tripId: string, data: {
    selectedBlocks: Array<{
      tempId: string
      blockType: BlockType
      placeName: string
      startTime: string | null
      durationMin: number | null
      cost: number | null
      memo: string | null
      dayNumber: number
      position: number
    }>
  }) => api.post<{ addedBlocks: Array<{ tempId: string; block: import('../types').Block }> }>(
    `/trips/${tripId}/ai/apply`, data
  ),
}
