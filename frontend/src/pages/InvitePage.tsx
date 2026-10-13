import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { MapPin, Calendar, Users, CheckCircle } from 'lucide-react'
import toast from 'react-hot-toast'
import { inviteApi } from '../services/inviteApi'

interface InviteInfo {
  tripId: string
  tripTitle: string
  destination: string
  startDate: string
  endDate: string
  memberCount: number
  inviterNickname: string
}

type Status = 'loading' | 'pending' | 'accepted' | 'error'

export default function InvitePage() {
  const { shareToken } = useParams<{ shareToken: string }>()
  const navigate = useNavigate()
  const [info, setInfo] = useState<InviteInfo | null>(null)
  const [status, setStatus] = useState<Status>('loading')

  useEffect(() => {
    if (!shareToken) return
    inviteApi.getInvite(shareToken)
      .then(({ data }) => { setInfo(data); setStatus('pending') })
      .catch(() => setStatus('error'))
  }, [shareToken])

  const handleAccept = async () => {
    if (!shareToken) return
    try {
      const { data } = await inviteApi.acceptInvite(shareToken)
      setStatus('accepted')
      setTimeout(() => navigate(`/trips/${data.tripId}`), 1200)
    } catch {
      toast.error('참여에 실패했습니다.')
    }
  }

  const nights = info
    ? Math.round((new Date(info.endDate).getTime() - new Date(info.startDate).getTime()) / 86400000)
    : 0

  return (
    <div className="min-h-screen bg-gradient-to-br from-violet-50 to-indigo-100 flex items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-6">
          <span className="text-4xl">✈️</span>
        </div>

        <div className="bg-white rounded-3xl shadow-lg overflow-hidden">
          <div className="h-36 bg-gradient-to-br from-violet-100 to-indigo-200 flex items-center justify-center text-6xl">
            🗺️
          </div>

          <div className="px-7 py-6">
            {status === 'loading' && (
              <div className="text-center py-4 text-gray-400 text-sm">불러오는 중...</div>
            )}

            {status === 'error' && (
              <div className="text-center py-4">
                <p className="font-bold text-gray-800 mb-1">유효하지 않은 초대 링크예요</p>
                <p className="text-sm text-gray-400">링크가 만료됐거나 올바르지 않습니다</p>
              </div>
            )}

            {status === 'pending' && info && (
              <>
                <p className="text-sm text-violet-600 font-medium mb-1">
                  {info.inviterNickname}님이 여행에 초대했어요
                </p>
                <h2 className="text-xl font-bold text-gray-800 mb-4">{info.tripTitle}</h2>

                <div className="space-y-2 mb-6">
                  <div className="flex items-center gap-2 text-sm text-gray-500">
                    <MapPin size={14} className="text-gray-400" />{info.destination}
                  </div>
                  <div className="flex items-center gap-2 text-sm text-gray-500">
                    <Calendar size={14} className="text-gray-400" />
                    {info.startDate} · {nights}박 {nights + 1}일
                  </div>
                  <div className="flex items-center gap-2 text-sm text-gray-500">
                    <Users size={14} className="text-gray-400" />현재 {info.memberCount}명 참여 중
                  </div>
                </div>

                <button onClick={handleAccept}
                  className="w-full py-3 rounded-2xl bg-violet-500 text-white text-sm font-medium hover:bg-violet-600 transition-colors">
                  참여하기
                </button>
              </>
            )}

            {status === 'accepted' && (
              <div className="text-center py-4">
                <CheckCircle size={40} className="text-violet-500 mx-auto mb-3" />
                <p className="font-bold text-gray-800 mb-1">참여 완료!</p>
                <p className="text-sm text-gray-400">에디터로 이동 중...</p>
              </div>
            )}
          </div>
        </div>

        {status === 'pending' && (
          <p className="text-center text-xs text-gray-400 mt-4">
            초대 코드: <span className="font-mono">{shareToken}</span>
          </p>
        )}
      </div>
    </div>
  )
}
