import axios from 'axios'
import api from './api'

export const inviteApi = {
  getInvite: (shareToken: string) =>
    axios.get<{
      tripId: string
      tripTitle: string
      destination: string
      startDate: string
      endDate: string
      memberCount: number
      inviterNickname: string
    }>(`/api/invite/${shareToken}`),

  acceptInvite: (shareToken: string) =>
    api.post<{ tripId: string }>(`/invite/${shareToken}/accept`),
}
