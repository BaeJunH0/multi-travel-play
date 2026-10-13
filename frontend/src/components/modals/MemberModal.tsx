import { useState, useEffect } from 'react'
import { X, Crown, Pencil, Eye, Trash2 } from 'lucide-react'
import toast from 'react-hot-toast'
import type { TripMember, TripRole } from '../../types'
import { memberApi } from '../../services/memberApi'

interface Props {
  open: boolean
  tripId: string
  myRole: TripRole
  onClose: () => void
}

const ROLE_LABEL: Record<TripRole, string> = {
  OWNER: '소유자',
  EDITOR: '편집자',
  VIEWER: '뷰어',
}

const RoleIcon = ({ role }: { role: TripRole }) => {
  if (role === 'OWNER') return <Crown size={13} className="text-amber-400" />
  if (role === 'EDITOR') return <Pencil size={13} className="text-violet-400" />
  return <Eye size={13} className="text-gray-400" />
}

export default function MemberModal({ open, tripId, myRole, onClose }: Props) {
  const [members, setMembers] = useState<TripMember[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!open) return
    setLoading(true)
    memberApi.getMembers(tripId)
      .then(({ data }) => setMembers(data))
      .catch(() => toast.error('멤버 목록을 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [open, tripId])

  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    if (open) window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [open, onClose])

  const handleRoleChange = async (userId: string, newRole: TripRole) => {
    try {
      await memberApi.updateRole(tripId, userId, newRole)
      setMembers((prev) => prev.map((m) => m.userId === userId ? { ...m, role: newRole } : m))
    } catch {
      toast.error('권한 변경에 실패했습니다.')
    }
  }

  const handleRemove = async (userId: string, nickname: string) => {
    if (!confirm(`${nickname} 님을 여행에서 내보낼까요?`)) return
    try {
      await memberApi.removeMember(tripId, userId)
      setMembers((prev) => prev.filter((m) => m.userId !== userId))
    } catch {
      toast.error('멤버 제거에 실패했습니다.')
    }
  }

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" onClick={onClose}>
      <div
        className="bg-white rounded-2xl shadow-xl w-full max-w-sm mx-4"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="text-base font-bold text-gray-800">멤버 관리</h2>
          <button onClick={onClose} className="p-1 rounded hover:bg-gray-100">
            <X size={18} />
          </button>
        </div>

        <div className="px-6 py-4 space-y-2 max-h-80 overflow-y-auto">
          {loading ? (
            <p className="text-sm text-gray-400 text-center py-4">불러오는 중...</p>
          ) : members.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-4">멤버가 없습니다.</p>
          ) : members.map((member) => (
            <div key={member.userId} className="flex items-center gap-3 py-2">
              <div className="w-8 h-8 rounded-full bg-violet-100 flex items-center justify-center text-xs font-bold text-violet-600 flex-shrink-0">
                {member.nickname.charAt(0)}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-gray-800 truncate">{member.nickname}</p>
                <div className="flex items-center gap-1 mt-0.5">
                  <RoleIcon role={member.role} />
                  <span className="text-xs text-gray-400">{ROLE_LABEL[member.role]}</span>
                </div>
              </div>

              {myRole === 'OWNER' && member.role !== 'OWNER' && (
                <div className="flex items-center gap-1 flex-shrink-0">
                  <select
                    value={member.role}
                    onChange={(e) => handleRoleChange(member.userId, e.target.value as TripRole)}
                    className="text-xs border border-gray-200 rounded-lg px-2 py-1 focus:outline-none focus:border-violet-400 bg-white"
                  >
                    <option value="EDITOR">편집자</option>
                    <option value="VIEWER">뷰어</option>
                  </select>
                  <button
                    onClick={() => handleRemove(member.userId, member.nickname)}
                    className="p-1 rounded hover:bg-red-50 text-gray-300 hover:text-red-400 transition-colors"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>

        <div className="px-6 py-3 border-t border-gray-100">
          <p className="text-xs text-gray-400 text-center">
            초대 링크로 멤버를 추가할 수 있어요
          </p>
        </div>
      </div>
    </div>
  )
}
