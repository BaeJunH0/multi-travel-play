import { useState, useEffect, useCallback } from 'react'
import { X } from 'lucide-react'
import type { Block, BlockType } from '../../types'
import { BLOCK_STYLE } from '../../types'

interface Props {
  open: boolean
  initialType?: BlockType
  initialDayNumber?: number
  editBlock?: Block | null
  onClose: () => void
  onSave: (data: Partial<Block> & { dayNumber: number; blockType: BlockType; placeName: string }) => void
}

export default function BlockModal({ open, initialType, initialDayNumber = 1, editBlock, onClose, onSave }: Props) {
  const [blockType, setBlockType] = useState<BlockType>(initialType ?? 'PLACE')
  const [placeName, setPlaceName] = useState('')
  const [startTime, setStartTime] = useState('')
  const [durationMin, setDurationMin] = useState('')
  const [cost, setCost] = useState('')
  const [memo, setMemo] = useState('')

  const stableOnClose = useCallback(onClose, [onClose])

  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') stableOnClose() }
    if (open) window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [open, stableOnClose])

  useEffect(() => {
    if (editBlock) {
      setBlockType(editBlock.blockType)
      setPlaceName(editBlock.placeName)
      setStartTime(editBlock.startTime ?? '')
      setDurationMin(editBlock.durationMin?.toString() ?? '')
      setCost(editBlock.cost?.toString() ?? '')
      setMemo(editBlock.memo ?? '')
    } else {
      setBlockType(initialType ?? 'PLACE')
      setPlaceName('')
      setStartTime('')
      setDurationMin('')
      setCost('')
      setMemo('')
    }
  }, [editBlock, initialType, open])

  if (!open) return null

  const handleSave = () => {
    if (!placeName.trim()) return
    onSave({
      ...(editBlock ?? {}),
      dayNumber: editBlock?.dayNumber ?? initialDayNumber,
      blockType,
      placeName: placeName.trim(),
      startTime: startTime || null,
      durationMin: durationMin ? Number(durationMin) : null,
      cost: cost ? Number(cost) : null,
      memo: memo || null,
    })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" onClick={onClose}>
      <div
        className="bg-white rounded-2xl shadow-xl w-full max-w-md mx-4"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="text-base font-bold text-gray-800">
            {editBlock ? '블록 편집' : '블록 추가'}
          </h2>
          <button onClick={onClose} className="p-1 rounded hover:bg-gray-100">
            <X size={18} />
          </button>
        </div>

        <div className="px-6 py-4 space-y-4">
          {/* 유형 */}
          <div>
            <label className="block text-xs font-medium text-gray-500 mb-2">유형</label>
            <div className="flex gap-2 flex-wrap">
              {(Object.keys(BLOCK_STYLE) as BlockType[]).map((t) => {
                const s = BLOCK_STYLE[t]
                const active = blockType === t
                return (
                  <button
                    key={t}
                    onClick={() => setBlockType(t)}
                    className="px-3 py-1.5 rounded-lg text-xs font-medium transition-all"
                    style={{
                      background: active ? s.bg : '#f3f4f6',
                      color: active ? s.border : '#6b7280',
                      border: active ? `1.5px solid ${s.border}` : '1.5px solid transparent',
                    }}
                  >
                    {s.label}
                  </button>
                )
              })}
            </div>
          </div>

          {/* 장소명 */}
          <div>
            <label className="block text-xs font-medium text-gray-500 mb-1">장소명 *</label>
            <input
              type="text"
              value={placeName}
              onChange={(e) => setPlaceName(e.target.value)}
              placeholder="장소 이름을 입력하세요"
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-violet-400"
            />
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">시작 시간 <span className="font-normal text-gray-400">(선택)</span></label>
              <input
                type="time"
                value={startTime}
                onChange={(e) => setStartTime(e.target.value)}
                className="w-full border border-gray-200 rounded-lg px-2 py-2 text-sm focus:outline-none focus:border-violet-400"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">소요(분) <span className="font-normal text-gray-400">(선택)</span></label>
              <input
                type="number"
                value={durationMin}
                onChange={(e) => setDurationMin(e.target.value)}
                placeholder="60"
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-violet-400"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">비용(원) <span className="font-normal text-gray-400">(선택)</span></label>
              <input
                type="number"
                value={cost}
                onChange={(e) => setCost(e.target.value)}
                placeholder="15000"
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-violet-400"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-500 mb-1">메모 <span className="font-normal text-gray-400">(선택)</span></label>
            <input
              type="text"
              value={memo}
              onChange={(e) => setMemo(e.target.value)}
              placeholder="메모를 입력하세요"
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-violet-400"
            />
          </div>
        </div>

        <div className="flex gap-2 px-6 py-4 border-t border-gray-100">
          <button
            onClick={onClose}
            className="flex-1 py-2 rounded-xl border border-gray-200 text-sm text-gray-600 hover:bg-gray-50 transition-colors"
          >
            취소
          </button>
          <button
            onClick={handleSave}
            disabled={!placeName.trim()}
            className="flex-1 py-2 rounded-xl bg-violet-500 text-white text-sm font-medium hover:bg-violet-600 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          >
            저장
          </button>
        </div>
      </div>
    </div>
  )
}
