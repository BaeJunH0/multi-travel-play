import api from './api'
import type { TripMember, TripRole } from '../types'

export const memberApi = {
  getMembers: (tripId: string) =>
    api.get<TripMember[]>(`/trips/${tripId}/members`),

  updateRole: (tripId: string, userId: string, role: TripRole) =>
    api.patch<TripMember>(`/trips/${tripId}/members/${userId}`, { role }),

  removeMember: (tripId: string, userId: string) =>
    api.delete(`/trips/${tripId}/members/${userId}`),
}
