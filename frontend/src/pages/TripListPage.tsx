import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, Users, MapPin, Calendar, X } from 'lucide-react'
import toast from 'react-hot-toast'
import { tripApi } from '../services/tripApi'
import { authApi } from '../services/authApi'
import { auth } from '../store/auth'
import type { Trip, TripRole } from '../types'

const ROLE_LABEL: Record<TripRole, { label: string; color: string }> = {
  OWNER:  { label: '오너',   color: 'bg-violet-100 text-violet-600' },
  EDITOR: { label: '편집자', color: 'bg-blue-100 text-blue-600' },
  VIEWER: { label: '뷰어',   color: 'bg-gray-100 text-gray-500' },
}

function TripCard({ trip }: { trip: Trip }) {
  const navigate = useNavigate()
  const role = ROLE_LABEL[trip.myRole]
  const start = new Date(trip.startDate)
  const end = new Date(trip.endDate)
  const nights = Math.round((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24))

  return (
    <div onClick={() => navigate(`/trips/${trip.id}`)}
      className="bg-white rounded-2xl shadow-sm hover:shadow-md transition-all cursor-pointer overflow-hidden group">
      <div className="h-32 bg-gradient-to-br from-violet-100 to-indigo-100 flex items-center justify-center text-5xl group-hover:scale-105 transition-transform">
        ✈️
      </div>
      <div className="p-4">
        <div className="flex items-start justify-between gap-2 mb-2">
          <h3 className="font-bold text-gray-800 text-sm leading-tight">{trip.title}</h3>
          <span className={`text-xs px-2 py-0.5 rounded-full font-medium flex-shrink-0 ${role.color}`}>
            {role.label}
          </span>
        </div>
        <div className="space-y-1">
          <div className="flex items-center gap-1.5 text-xs text-gray-400">
            <MapPin size={11} />{trip.destination}
          </div>
          <div className="flex items-center gap-1.5 text-xs text-gray-400">
            <Calendar size={11} />{trip.startDate} · {nights}박 {nights + 1}일
          </div>
          <div className="flex items-center gap-1.5 text-xs text-gray-400">
            <Users size={11} />{trip.memberCount}명
          </div>
        </div>
      </div>
    </div>
  )
}

export default function TripListPage() {
  const navigate = useNavigate()
  const [trips, setTrips] = useState<Trip[]>([])
  const [loading, setLoading] = useState(true)

  // 새 여행 만들기 모달 상태
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [newTitle, setNewTitle] = useState('')
  const [newDestination, setNewDestination] = useState('')
  const [newStartDate, setNewStartDate] = useState('')
  const [newEndDate, setNewEndDate] = useState('')
  const [createLoading, setCreateLoading] = useState(false)

  useEffect(() => {
    tripApi.getTrips()
      .then(({ data }) => setTrips(data))
      .catch(() => toast.error('여행 목록을 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [])

  const handleLogout = async () => {
    await authApi.logout().catch(() => {})
    auth.clearToken()
    navigate('/login')
  }

  const handleCreateTrip = async (e: React.FormEvent) => {
    e.preventDefault()
    setCreateLoading(true)
    try {
      const { data: newTrip } = await tripApi.createTrip({
        title: newTitle,
        destination: newDestination,
        startDate: newStartDate,
        endDate: newEndDate,
      })
      setTrips((prev) => [newTrip, ...prev])
      setCreateModalOpen(false)
      navigate(`/trips/${newTrip.id}`)
    } catch {
      toast.error('여행 생성에 실패했습니다.')
    } finally {
      setCreateLoading(false)
    }
  }

  const closeModal = () => {
    setCreateModalOpen(false)
    setNewTitle('')
    setNewDestination('')
    setNewStartDate('')
    setNewEndDate('')
  }

  const isCreateDisabled = createLoading || !newTitle || !newDestination || !newStartDate || !newEndDate || (!!newStartDate && !!newEndDate && newEndDate < newStartDate)

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white border-b border-gray-100 px-6 py-4">
        <div className="max-w-5xl mx-auto flex items-center justify-between">
          <span className="text-xl font-bold text-violet-600">✈️ TripPlanner</span>
          <button onClick={handleLogout}
            className="text-xs text-gray-400 hover:text-gray-600 transition-colors">
            로그아웃
          </button>
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-6 py-8">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-xl font-bold text-gray-800">내 여행</h1>
            <p className="text-sm text-gray-400 mt-0.5">총 {trips.length}개의 여행</p>
          </div>
          <button
            onClick={() => setCreateModalOpen(true)}
            className="flex items-center gap-2 px-4 py-2 bg-violet-500 text-white rounded-xl text-sm font-medium hover:bg-violet-600 transition-colors">
            <Plus size={16} />새 여행 만들기
          </button>
        </div>

        {loading ? (
          <div className="text-center py-20 text-gray-300 text-sm">불러오는 중...</div>
        ) : trips.length === 0 ? (
          <div className="text-center py-20 text-gray-300 text-sm">아직 여행이 없어요</div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {trips.map((trip) => <TripCard key={trip.id} trip={trip} />)}
          </div>
        )}
      </main>

      {createModalOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/30"
          onClick={closeModal}
        >
          <div
            className="bg-white rounded-3xl shadow-lg px-8 py-8 w-full max-w-sm mx-4"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-base font-bold text-gray-800">새 여행 만들기</h2>
              <button type="button" onClick={closeModal} className="text-gray-300 hover:text-gray-500">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleCreateTrip} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1.5">여행 제목</label>
                <input
                  type="text"
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  placeholder="예: 오사카 벚꽃 여행"
                  required
                  className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-violet-400"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1.5">목적지</label>
                <input
                  type="text"
                  value={newDestination}
                  onChange={(e) => setNewDestination(e.target.value)}
                  placeholder="예: 일본 오사카"
                  required
                  className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-violet-400"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1.5">시작 날짜</label>
                <input
                  type="date"
                  value={newStartDate}
                  onChange={(e) => setNewStartDate(e.target.value)}
                  required
                  className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-violet-400"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1.5">종료 날짜</label>
                <input
                  type="date"
                  value={newEndDate}
                  onChange={(e) => setNewEndDate(e.target.value)}
                  required
                  className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-violet-400"
                />
                {newStartDate && newEndDate && newEndDate < newStartDate && (
                  <p className="text-xs text-red-400 mt-1">종료 날짜는 시작 날짜 이후여야 합니다.</p>
                )}
              </div>

              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={closeModal}
                  className="flex-1 py-3 rounded-2xl border border-gray-200 text-sm text-gray-500 hover:bg-gray-50 transition-colors"
                >
                  취소
                </button>
                <button
                  type="submit"
                  disabled={isCreateDisabled}
                  className="flex-1 py-3 rounded-2xl bg-violet-500 text-white text-sm font-medium hover:bg-violet-600 disabled:opacity-60 transition-colors"
                >
                  {createLoading ? '만드는 중...' : '만들기'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
