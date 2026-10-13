import api from './api'
import type { BudgetItem, BudgetCategory } from '../types'

export const budgetApi = {
  getItems: (tripId: string) =>
    api.get<BudgetItem[]>(`/trips/${tripId}/budget`),

  createItem: (tripId: string, data: { category: BudgetCategory; amount: number; memo?: string | null }) =>
    api.post<BudgetItem>(`/trips/${tripId}/budget`, data),

  updateItem: (tripId: string, itemId: string, data: Partial<{ category: BudgetCategory; amount: number; memo: string | null }>) =>
    api.patch<BudgetItem>(`/trips/${tripId}/budget/${itemId}`, data),

  deleteItem: (tripId: string, itemId: string) =>
    api.delete(`/trips/${tripId}/budget/${itemId}`),
}
