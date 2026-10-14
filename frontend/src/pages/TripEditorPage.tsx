import { useState, useEffect } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ChevronLeft, Link2, Sparkles, Users, Wallet, Plus } from 'lucide-react'
import toast from 'react-hot-toast'
import { isAxiosError } from 'axios'
import type { Block, BlockType, TripDetail, Presence, AiGeneratedBlock } from '../types'
import { BLOCK_STYLE } from '../types'
import { tripApi } from '../services/tripApi'
import { blockApi } from '../services/blockApi'
import { useTripBlocks, lockedMessage } from '../hooks/useTripBlocks'
import { useWebSocket } from '../hooks/useWebSocket'
import DayColumn from '../components/DayColumn'
import BlockModal from '../components/modals/BlockModal'
import AiModal from '../components/modals/AiModal'
import MemberModal from '../components/modals/MemberModal'
import BudgetModal from '../components/modals/BudgetModal'
import AvatarStack from '../components/ui/AvatarStack'

export default function TripEditorPage() {
  const { tripId } = useParams<{ tripId: string }>()!
  const [trip, setTrip] = useState<TripDetail | null>(null)
  const [presence, setPresence] = useState<Presence[]>([])
  // 활성 Day: 사용자가 마지막으로 클릭(포인터 다운)한 Day 컬럼
  const [activeDay, setActiveDay] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)

  const [blockModalOpen, setBlockModalOpen] = useState(false)
  const [aiModalOpen, setAiModalOpen] = useState(false)
  const [memberModalOpen, setMemberModalOpen] = useState(false)
  const [budgetModalOpen, setBudgetModalOpen] = useState(false)
  const [editingBlock, setEditingBlock] = useState<Block | null>(null)
  const [modalInitialType, setModalInitialType] = useState<BlockType | undefined>()
  const [modalInitialDay, setModalInitialDay] = useState(1)

  const [draggedBlockId, setDraggedBlockId] = useState<string | null>(null)
  const [dragTargetInfo, setDragTargetInfo] = useState<{ blockId: string; pos: 'above' | 'below' } | null>(null)

  const { blocks, setBlocks, handleWsMessage, addBlock, updateBlock, moveBlock, deleteBlock, lockBlock, unlockBlock } =
    useTripBlocks(tripId!, [])

  // 초기 데이터 로드
  useEffect(() => {
    if (!tripId) return
    Promise.all([tripApi.getTrip(tripId), blockApi.getBlocks(tripId)])
      .then(([{ data: tripData }, { data: blockData }]) => {
        setTrip(tripData)
        setBlocks(blockData)
      })
      .catch(() => toast.error('데이터를 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [tripId])

  // WebSocket
  useWebSocket({
    tripId: tripId!,
    activeDay,
    onMessage: (msg) => {
      if (msg.action === 'PRESENCE') {
        setPresence((msg.payload as any).users)
      } else {
        handleWsMessage(msg)
      }
    },
  })

  if (loading || !trip) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <span className="text-2xl animate-pulse">✈️</span>
      </div>
    )
  }

  const isViewer = trip.myRole === 'VIEWER'

  // Block CRUD
  const handleAddBlock = (dayNumber: number, type?: BlockType) => {
    setEditingBlock(null)
    setModalInitialDay(dayNumber)
    setModalInitialType(type)
    setBlockModalOpen(true)
  }

  const handleEditBlock = async (block: Block) => {
    try {
      await lockBlock(block.id)
    } catch (err) {
      toast.error(isAxiosError(err) && err.response?.status === 423
        ? lockedMessage(block.lockedByNickname)
        : '편집을 시작하지 못했습니다.')
      return
    }
    setEditingBlock(block)
    setBlockModalOpen(true)
  }

  const handleCloseBlockModal = () => {
    if (editingBlock) unlockBlock(editingBlock.id).catch(() => {})
    setBlockModalOpen(false)
    setEditingBlock(null)
  }

  const handleSaveBlock = async (data: Partial<Block> & { dayNumber: number; blockType: BlockType; placeName: string }) => {
    try {
      if (editingBlock) {
        await updateBlock(editingBlock.id, {
          blockType: data.blockType, placeName: data.placeName,
          startTime: data.startTime ?? null, durationMin: data.durationMin ?? null,
          cost: data.cost ?? null, memo: data.memo ?? null,
        })
        unlockBlock(editingBlock.id).catch(() => {})
      } else {
        await addBlock({
          dayNumber: data.dayNumber, blockType: data.blockType, placeName: data.placeName,
          startTime: data.startTime ?? null, durationMin: data.durationMin ?? null,
          cost: data.cost ?? null, memo: data.memo ?? null,
        })
      }
      setBlockModalOpen(false)
      setEditingBlock(null)
    } catch { /* 훅 내부에서 토스트 처리 */ }
  }

  const handleToolbarType = (type: BlockType) => {
    setEditingBlock(null); setModalInitialType(type); setModalInitialDay(trip.days[0]?.dayNumber ?? 1); setBlockModalOpen(true)
  }

  // Day 관리 (백엔드: days = startDate~endDate 자동계산)
  const handleAddDay = async () => {
    const newEndDate = new Date(trip.endDate)
    newEndDate.setDate(newEndDate.getDate() + 1)
    try {
      const { data } = await tripApi.updateTrip(trip.id, { endDate: newEndDate.toISOString().split('T')[0] })
      setTrip(data)
    } catch { toast.error('Day 추가에 실패했습니다.') }
  }

  const handleDeleteDay = async (dayNumber: number) => {
    // 해당 Day 블록 삭제 → 마지막 Day면 endDate 하루 감소
    const dayBlocks = blocks.filter((b) => b.dayNumber === dayNumber)
    try {
      await Promise.all(dayBlocks.map((b) => deleteBlock(b.id)))
      if (dayNumber === trip.days.length) {
        const newEndDate = new Date(trip.endDate)
        newEndDate.setDate(newEndDate.getDate() - 1)
        const { data } = await tripApi.updateTrip(trip.id, { endDate: newEndDate.toISOString().split('T')[0] })
        setTrip(data)
      } else {
        // 중간 Day 삭제: 이후 블록의 dayNumber 조정
        const laterBlocks = blocks.filter((b) => b.dayNumber > dayNumber)
        await Promise.all(laterBlocks.map((b) =>
          blockApi.moveBlock(trip.id, b.id, { dayNumber: b.dayNumber - 1, position: b.position, version: b.version })
        ))
        const newEndDate = new Date(trip.endDate)
        newEndDate.setDate(newEndDate.getDate() - 1)
        const { data } = await tripApi.updateTrip(trip.id, { endDate: newEndDate.toISOString().split('T')[0] })
        setTrip(data)
      }
    } catch { toast.error('Day 삭제에 실패했습니다.') }
  }

  const handleChangeDate = async (dayNumber: number, newDate: string) => {
    const oldDate = trip.days.find((d) => d.dayNumber === dayNumber)?.date
    if (!oldDate) return
    const deltaDays = Math.round((new Date(newDate).getTime() - new Date(oldDate).getTime()) / 86400000)
    const patch: Record<string, string> = {}
    if (dayNumber === 1) {
      patch.startDate = newDate
    } else if (dayNumber === trip.days.length) {
      patch.endDate = newDate
    } else {
      // 중간 Day: 전체 범위 이동
      const newStart = new Date(trip.startDate); newStart.setDate(newStart.getDate() + deltaDays)
      const newEnd = new Date(trip.endDate);     newEnd.setDate(newEnd.getDate() + deltaDays)
      patch.startDate = newStart.toISOString().split('T')[0]
      patch.endDate   = newEnd.toISOString().split('T')[0]
    }
    try {
      const { data } = await tripApi.updateTrip(trip.id, patch)
      setTrip(data)
    } catch { toast.error('날짜 변경에 실패했습니다.') }
  }

  // AI apply
  const handleAiApply = (aiBlocks: Array<AiGeneratedBlock & { dayNumber: number }>) => {
    // AiModal에서 apply API 호출 후 반환된 블록을 직접 setBlocks에 추가
    const newBlocks: Block[] = aiBlocks as any
    setBlocks((prev) => [...prev, ...newBlocks])
  }

  // DnD
  const calcPosition = (sorted: Block[], targetIdx: number, pos: 'above' | 'below'): number => {
    if (pos === 'above') {
      const prev = sorted[targetIdx - 1]
      return prev ? (prev.position + sorted[targetIdx].position) / 2 : sorted[targetIdx].position - 1.0
    } else {
      const next = sorted[targetIdx + 1]
      return next ? (sorted[targetIdx].position + next.position) / 2 : sorted[targetIdx].position + 1.0
    }
  }

  const handleDragStart = (e: React.DragEvent, block: Block) => {
    e.dataTransfer.setData('blockId', block.id)
    setDraggedBlockId(block.id)
  }

  const handleDragEnd = () => { setDraggedBlockId(null); setDragTargetInfo(null) }

  const handleDragOverBlock = (e: React.DragEvent, blockId: string) => {
    e.preventDefault()
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
    setDragTargetInfo({ blockId, pos: e.clientY < rect.top + rect.height / 2 ? 'above' : 'below' })
  }

  const handleDropOnBlock = (e: React.DragEvent, targetBlockId: string) => {
    e.preventDefault(); e.stopPropagation()
    const pos = dragTargetInfo?.pos ?? 'below'
    setDraggedBlockId(null); setDragTargetInfo(null)
    const srcId = e.dataTransfer.getData('blockId')
    if (!srcId || srcId === targetBlockId) return

    const target = blocks.find((b) => b.id === targetBlockId)!
    const dayBlocks = blocks.filter((b) => b.dayNumber === target.dayNumber).sort((a, b) => a.position - b.position)
    const targetIdx = dayBlocks.findIndex((b) => b.id === targetBlockId)
    const newPosition = calcPosition(dayBlocks, targetIdx, pos)
    moveBlock(srcId, target.dayNumber, newPosition)
  }

  const handleDropOnColumn = (e: React.DragEvent, dayNumber: number) => {
    e.preventDefault()
    setDraggedBlockId(null); setDragTargetInfo(null)
    const srcId = e.dataTransfer.getData('blockId')
    if (!srcId) return
    const dayBlocks = blocks.filter((b) => b.dayNumber === dayNumber)
    const newPosition = dayBlocks.length > 0
      ? Math.max(...dayBlocks.map((b) => b.position)) + 1.0
      : 1.0
    moveBlock(srcId, dayNumber, newPosition)
  }

  const handleSortByTime = async (dayNumber: number) => {
    const dayBlocks = blocks.filter((b) => b.dayNumber === dayNumber)
    const sorted = [...dayBlocks].sort((a, b) => {
      if (a.startTime && b.startTime) return a.startTime.localeCompare(b.startTime)
      if (a.startTime) return -1
      if (b.startTime) return 1
      return a.position - b.position
    })
    const changed = sorted
      .map((block, idx) => ({ block, newPosition: idx + 1.0 }))
      .filter(({ block, newPosition }) => block.position !== newPosition)
    try {
      await Promise.all(changed.map(({ block, newPosition }) => moveBlock(block.id, dayNumber, newPosition)))
    } catch { /* moveBlock 내부에서 토스트 처리 */ }
  }

  const handleShareLink = async () => {
    try {
      const { data } = await tripApi.createInvite(trip.id)
      navigator.clipboard.writeText(data.inviteUrl)
      toast.success('초대 링크가 복사됐어요!')
    } catch { toast.error('링크 생성에 실패했습니다.') }
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      {/* TopBar */}
      <header className="bg-white border-b border-gray-100 px-4 py-3 flex items-center gap-3 flex-shrink-0">
        <Link to="/" className="p-1.5 rounded-lg hover:bg-gray-100 text-gray-400 hover:text-gray-700 transition-colors">
          <ChevronLeft size={18} />
        </Link>
        <div className="flex-1 min-w-0">
          <h1 className="font-bold text-gray-800 text-sm truncate">{trip.title}</h1>
        </div>
        <div className="flex items-center gap-3">
          {presence.length > 0 && (
            <>
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-green-400 animate-pulse" />
                <span className="text-xs text-gray-400">{presence.length}명 편집 중</span>
              </div>
              <AvatarStack presence={presence} max={3} />
            </>
          )}
          <button onClick={() => setMemberModalOpen(true)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-gray-200 text-xs text-gray-600 hover:bg-gray-50 transition-colors">
            <Users size={13} /> 멤버
          </button>
          <button onClick={() => setBudgetModalOpen(true)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-gray-200 text-xs text-gray-600 hover:bg-gray-50 transition-colors">
            <Wallet size={13} /> 예산
          </button>
          {!isViewer && (
            <>
              <button onClick={handleShareLink}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-gray-200 text-xs text-gray-600 hover:bg-gray-50 transition-colors">
                <Link2 size={13} /> 공유
              </button>
              <button onClick={() => setAiModalOpen(true)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-violet-500 text-white text-xs font-medium hover:bg-violet-600 transition-colors">
                <Sparkles size={13} /> AI 생성
              </button>
            </>
          )}
        </div>
      </header>

      {/* Toolbar */}
      {!isViewer && (
        <div className="bg-white border-b border-gray-100 px-4 py-2 flex items-center gap-2 flex-shrink-0">
          <span className="text-xs text-gray-400 mr-1">추가</span>
          {(Object.keys(BLOCK_STYLE) as BlockType[]).map((t) => {
            const s = BLOCK_STYLE[t]
            return (
              <button key={t} onClick={() => handleToolbarType(t)}
                className="px-3 py-1.5 rounded-lg text-xs font-medium transition-all hover:opacity-80"
                style={{ background: s.bg, color: s.border, border: `1px solid ${s.badge}` }}>
                {s.label}
              </button>
            )
          })}
        </div>
      )}

      {/* Board */}
      <div className="flex-1 overflow-x-auto px-4 py-4">
        <div className="flex gap-4 min-w-max">
          {trip.days.map((day) => (
            <div key={day.dayNumber} className="contents" onPointerDown={() => setActiveDay(day.dayNumber)}>
              <DayColumn
                day={day}
                blocks={blocks.filter((b) => b.dayNumber === day.dayNumber)}
                isViewer={isViewer}
                canDelete={trip.days.length > 1}
                draggedBlockId={draggedBlockId}
                dragTargetInfo={dragTargetInfo}
                onAddBlock={handleAddBlock}
                onDeleteDay={handleDeleteDay}
                onChangeDate={handleChangeDate}
                onSortByTime={handleSortByTime}
                onEditBlock={handleEditBlock}
                onDeleteBlock={deleteBlock}
                onDragStart={handleDragStart}
                onDragEnd={handleDragEnd}
                onDragOverBlock={handleDragOverBlock}
                onDropOnBlock={handleDropOnBlock}
                onDropOnColumn={handleDropOnColumn}
              />
            </div>
          ))}

          {!isViewer && (
            <div className="flex-shrink-0 flex items-start pt-1">
              <button onClick={handleAddDay}
                className="w-12 h-12 rounded-2xl border-2 border-dashed border-gray-200 hover:border-violet-300 hover:bg-violet-50 flex items-center justify-center text-gray-300 hover:text-violet-400 transition-all">
                <Plus size={20} />
              </button>
            </div>
          )}
        </div>
      </div>

      <BlockModal
        open={blockModalOpen}
        initialType={modalInitialType}
        initialDayNumber={modalInitialDay}
        editBlock={editingBlock}
        onClose={handleCloseBlockModal}
        onSave={handleSaveBlock}
      />
      <AiModal
        open={aiModalOpen}
        tripId={tripId!}
        days={trip.days}
        blocks={blocks}
        onClose={() => setAiModalOpen(false)}
        onApply={handleAiApply}
      />
      <MemberModal
        open={memberModalOpen}
        tripId={tripId!}
        myRole={trip.myRole}
        onClose={() => setMemberModalOpen(false)}
      />
      <BudgetModal
        open={budgetModalOpen}
        tripId={tripId!}
        onClose={() => setBudgetModalOpen(false)}
      />
    </div>
  )
}
