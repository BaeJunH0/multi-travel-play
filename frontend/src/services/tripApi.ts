import api from './api'
import type { Trip, TripDetail } from '../types'

export const tripApi = {
  getTrips: () =>
    api.get<Trip[]>('/trips'),

  getTrip: (tripId: string) =>
    api.get<TripDetail>(`/trips/${tripId}`),

  createTrip: (data: { title: string; destination: string; startDate: string; endDate: string }) =>
    api.post<TripDetail>('/trips', data),

  updateTrip: (tripId: string, data: Partial<{ title: string; destination: string; startDate: string; endDate: string }>) =>
    api.patch<TripDetail>(`/trips/${tripId}`, data),

  deleteTrip: (tripId: string) =>
    api.delete(`/trips/${tripId}`),

  createInvite: (tripId: string) =>
    api.post<{ shareToken: string; inviteUrl: string }>(`/trips/${tripId}/invite`),

  // 활성 Day 변경 (Presence)
  updatePresence: (tripId: string, activeDay: number | null) =>
    api.post(`/trips/${tripId}/presence`, { activeDay }),
}
