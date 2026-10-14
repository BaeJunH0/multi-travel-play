import { useState, useEffect } from 'react'
import { X, Sparkles, ChevronLeft, Check } from 'lucide-react'
import toast from 'react-hot-toast'
import { isAxiosError } from 'axios'
import type { Block, BlockType, TripDay, AiGeneratedBlock } from '../../types'
import { BLOCK_STYLE } from '../../types'
import { aiApi } from '../../services/aiApi'
import BlockTypeBadge from '../ui/BlockTypeBadge'

interface Props {
  open: boolean
  tripId: string
  days: TripDay[]
  blocks: Block[]
  onClose: () => void
  onApply: (blocks: Array<AiGeneratedBlock & { dayNumber: number }>) => void
}

type Step = 1 | 2 | 3

export default function AiModal({ open, tripId, days, blocks, onClose, onApply }: Props) {
  const [step, setStep] = useState<Step>(1)
  const [selectedTypes, setSelectedTypes] = useState<BlockType[]>([])
  const [allTypes, setAllTypes] = useState(true)
  const [style, setStyle] = useState('맛집 위주')
  const [intensity, setIntensity] = useState('보통')
  const [extra, setExtra] = useState('')
  const [generated, setGenerated] = useState<Array<AiGeneratedBlock & { dayNumber: number; checked: boolean }>>([])
  const [appliedCount, setAppliedCount] = useState(0)
  const [loading, setLoading] = useState(false)

  const [prevOpen, setPrevOpen] = useState(open)

  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    if (open) window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [open, onClose])

  // 모달 닫힐 때 상태 초기화
  if (open !== prevOpen) {
    setPrevOpen(open)
    if (!open) { setStep(1); setGenerated([]) }
  }

  if (!open) return null

  const TYPES: BlockType[] = ['HOTEL', 'FOOD', 'CAFE', 'PLACE', 'TRANSPORT']

  const toggleType = (t: BlockType) => {
    setAllTypes(false)
    setSelectedTypes((prev) => prev.includes(t) ? prev.filter((x) => x !== t) : [...prev, t])
  }

  const handleGenerate = async () => {
    setLoading(true)
    try {
      const { data } = await aiApi.generate(tripId, {
        tags: allTypes ? [] : selectedTypes,
        targetDays: days.map((d) => d.dayNumber),
        style,
        intensity,
        extraRequest: extra || null,
      })
      setGenerated(data.blocks.map((b) => ({ ...b, dayNumber: b.suggestedDay, checked: true })))
      setStep(2)
    } catch (err) {
      // 503 AI_UNAVAILABLE(잠시 후 재시도) / 502 등은 서버 메시지를 그대로 보여준다
      toast.error((isAxiosError(err) && err.response?.data?.message) || 'AI 생성에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  const handleApply = async () => {
    const selected = generated.filter((b) => b.checked)
    setLoading(true)
    try {
      // 각 Day의 마지막 position 뒤로 배치
      const dayMaxPos: Record<number, number> = {}
      const selectedBlocks = selected.map((b) => {
        const currentMax = dayMaxPos[b.dayNumber]
          ?? Math.max(0, ...blocks.filter((x) => x.dayNumber === b.dayNumber).map((x) => x.position))
        dayMaxPos[b.dayNumber] = currentMax + 1.0
        return {
          tempId: b.tempId,
          blockType: b.blockType,
          placeName: b.placeName,
          startTime: b.startTime,
          durationMin: b.durationMin,
          cost: b.cost,
          memo: b.memo,
          dayNumber: b.dayNumber,
          position: dayMaxPos[b.dayNumber],
        }
      })

      const { data } = await aiApi.apply(tripId, { selectedBlocks })
      const addedBlocks = data.addedBlocks.map(({ block }) => block)
      onApply(addedBlocks as any)
      setAppliedCount(selected.length)
      setStep(3)
    } catch {
      toast.error('일정 적용에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  const checkedCount = generated.filter((b) => b.checked).length

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg mx-4" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <div className="flex items-center gap-2">
            <Sparkles size={18} className="text-violet-500" />
            <h2 className="text-base font-bold text-gray-800">AI 일정 생성</h2>
            <span className="text-xs text-gray-400">단계 {step}/3</span>
          </div>
          <button onClick={onClose} className="p-1 rounded hover:bg-gray-100"><X size={18} /></button>
        </div>

        {/* Step 1 */}
        {step === 1 && (
          <div className="px-6 py-4 space-y-4">
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-2">생성 항목</label>
              <div className="flex flex-wrap gap-2">
                <button onClick={() => { setAllTypes(true); setSelectedTypes([]) }}
                  className={`px-3 py-1.5 rounded-full text-xs font-medium border transition-colors ${allTypes ? 'bg-violet-500 text-white border-violet-500' : 'bg-white text-gray-500 border-gray-200'}`}>
                  전체
                </button>
                {TYPES.map((t) => {
                  const s = BLOCK_STYLE[t]; const active = selectedTypes.includes(t)
                  return (
                    <button key={t} onClick={() => toggleType(t)}
                      className="px-3 py-1.5 rounded-full text-xs font-medium border transition-all"
                      style={{ background: active ? s.bg : 'white', color: active ? s.border : '#6b7280', borderColor: active ? s.border : '#e5e7eb' }}>
                      {s.label}
                    </button>
                  )
                })}
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">여행 스타일</label>
                <select value={style} onChange={(e) => setStyle(e.target.value)}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-violet-400">
                  {['맛집 위주', '관광 위주', '쇼핑 위주', '휴양 위주'].map((v) => <option key={v}>{v}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">하루 강도</label>
                <select value={intensity} onChange={(e) => setIntensity(e.target.value)}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-violet-400">
                  {['여유롭게', '보통', '빡빡하게'].map((v) => <option key={v}>{v}</option>)}
                </select>
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">추가 요청 사항 <span className="font-normal text-gray-400">(선택)</span></label>
              <input type="text" value={extra} onChange={(e) => setExtra(e.target.value)}
                placeholder="예: 가족 동반이에요, 아이가 있어요"
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-violet-400" />
            </div>

            <button onClick={handleGenerate} disabled={loading}
              className="w-full py-2.5 rounded-xl bg-violet-500 text-white text-sm font-medium hover:bg-violet-600 disabled:opacity-60 transition-colors flex items-center justify-center gap-2">
              <Sparkles size={14} />{loading ? '생성 중...' : '생성하기'}
            </button>
          </div>
        )}

        {/* Step 2 */}
        {step === 2 && (
          <div className="px-6 py-4">
            <div className="flex items-center justify-between mb-3">
              <button onClick={() => setStep(1)} className="flex items-center gap-1 text-xs text-gray-500 hover:text-gray-700">
                <ChevronLeft size={14} /> 다시 설정
              </button>
              <div className="flex gap-2 text-xs">
                <button onClick={() => setGenerated((p) => p.map((b) => ({ ...b, checked: true })))} className="text-violet-500 hover:underline">전체 선택</button>
                <span className="text-gray-300">|</span>
                <button onClick={() => setGenerated((p) => p.map((b) => ({ ...b, checked: false })))} className="text-gray-400 hover:underline">전체 해제</button>
              </div>
            </div>

            <div className="space-y-2 max-h-72 overflow-y-auto">
              {generated.map((b, i) => (
                <div key={b.tempId} className={`flex items-center gap-3 p-2.5 rounded-xl border transition-opacity ${b.checked ? 'border-gray-100 bg-gray-50' : 'border-gray-100 bg-white opacity-40'}`}>
                  <button onClick={() => setGenerated((p) => p.map((x, j) => j === i ? { ...x, checked: !x.checked } : x))}
                    className={`w-5 h-5 rounded flex items-center justify-center flex-shrink-0 ${b.checked ? 'bg-violet-500' : 'border border-gray-300'}`}>
                    {b.checked && <Check size={12} className="text-white" />}
                  </button>
                  <BlockTypeBadge type={b.blockType} />
                  <span className="flex-1 text-sm text-gray-800 truncate">{b.placeName}</span>
                  <span className="text-xs text-gray-400 flex-shrink-0">{b.startTime ?? '-'}</span>
                  <select value={b.dayNumber} disabled={!b.checked}
                    onChange={(e) => setGenerated((p) => p.map((x, j) => j === i ? { ...x, dayNumber: Number(e.target.value) } : x))}
                    className="border border-gray-200 rounded px-1.5 py-1 text-xs focus:outline-none disabled:opacity-50">
                    {days.map((d) => <option key={d.dayNumber} value={d.dayNumber}>Day {d.dayNumber}</option>)}
                  </select>
                </div>
              ))}
            </div>

            <div className="flex items-center justify-between mt-4 pt-3 border-t border-gray-100">
              <span className="text-xs text-gray-500">선택된 {checkedCount}개</span>
              <button onClick={handleApply} disabled={checkedCount === 0 || loading}
                className="px-5 py-2 rounded-xl bg-violet-500 text-white text-sm font-medium hover:bg-violet-600 disabled:opacity-40 transition-colors">
                {loading ? '추가 중...' : '에디터에 추가하기'}
              </button>
            </div>
          </div>
        )}

        {/* Step 3 */}
        {step === 3 && (
          <div className="px-6 py-10 text-center">
            <div className="w-14 h-14 bg-violet-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <Check size={28} className="text-violet-500" />
            </div>
            <p className="text-base font-bold text-gray-800 mb-1">{appliedCount}개 블록이 추가됐어요</p>
            <p className="text-sm text-gray-400 mb-6">에디터에서 자유롭게 수정해 보세요</p>
            <button onClick={onClose}
              className="px-8 py-2.5 rounded-xl bg-violet-500 text-white text-sm font-medium hover:bg-violet-600 transition-colors">
              확인
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
