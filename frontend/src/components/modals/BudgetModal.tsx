import { useState, useEffect } from 'react'
import { X, Plus, Trash2, Check, Pencil } from 'lucide-react'
import toast from 'react-hot-toast'
import type { BudgetItem, BudgetCategory } from '../../types'
import { budgetApi } from '../../services/budgetApi'

interface Props {
  open: boolean
  tripId: string
  onClose: () => void
}

const CATEGORY_LABEL: Record<BudgetCategory, string> = {
  HOTEL: '숙박',
  FOOD: '식비',
  CAFE: '카페',
  PLACE: '장소',
  TRANSPORT: '교통',
  FLIGHT: '항공',
  ETC: '기타',
}

const CATEGORY_COLOR: Record<BudgetCategory, string> = {
  HOTEL: 'bg-emerald-50 text-emerald-600',
  FOOD: 'bg-orange-50 text-orange-600',
  CAFE: 'bg-amber-50 text-amber-600',
  PLACE: 'bg-violet-50 text-violet-600',
  TRANSPORT: 'bg-sky-50 text-sky-600',
  FLIGHT: 'bg-blue-50 text-blue-600',
  ETC: 'bg-gray-100 text-gray-500',
}

const CATEGORIES = Object.keys(CATEGORY_LABEL) as BudgetCategory[]

interface EditState {
  category: BudgetCategory
  amount: string
  memo: string
}

const emptyEdit = (): EditState => ({ category: 'ETC', amount: '', memo: '' })

export default function BudgetModal({ open, tripId, onClose }: Props) {
  const [items, setItems] = useState<BudgetItem[]>([])
  const [loading, setLoading] = useState(false)
  const [prevFetchKey, setPrevFetchKey] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [newItem, setNewItem] = useState<EditState>(emptyEdit())
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editState, setEditState] = useState<EditState>(emptyEdit())

  // 모달이 열리거나 tripId가 바뀌면 로딩 상태로 전환
  const fetchKey = open ? tripId : null
  if (fetchKey !== prevFetchKey) {
    setPrevFetchKey(fetchKey)
    if (fetchKey) setLoading(true)
  }

  useEffect(() => {
    if (!open) return
    budgetApi.getItems(tripId)
      .then(({ data }) => setItems(data))
      .catch(() => toast.error('예산을 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [open, tripId])

  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    if (open) window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [open, onClose])

  const handleAdd = async () => {
    if (!newItem.amount) return
    try {
      const { data } = await budgetApi.createItem(tripId, {
        category: newItem.category,
        amount: Number(newItem.amount),
        memo: newItem.memo || null,
      })
      setItems((prev) => [...prev, data])
      setNewItem(emptyEdit())
      setAdding(false)
    } catch {
      toast.error('항목 추가에 실패했습니다.')
    }
  }

  const handleEditSave = async (id: string) => {
    if (!editState.amount) return
    try {
      const { data } = await budgetApi.updateItem(tripId, id, {
        category: editState.category,
        amount: Number(editState.amount),
        memo: editState.memo || null,
      })
      setItems((prev) => prev.map((i) => i.id === id ? data : i))
      setEditingId(null)
    } catch {
      toast.error('항목 수정에 실패했습니다.')
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await budgetApi.deleteItem(tripId, id)
      setItems((prev) => prev.filter((i) => i.id !== id))
    } catch {
      toast.error('항목 삭제에 실패했습니다.')
    }
  }

  const startEdit = (item: BudgetItem) => {
    setEditingId(item.id)
    setEditState({ category: item.category, amount: String(item.amount), memo: item.memo ?? '' })
  }

  const total = items.reduce((sum, i) => sum + i.amount, 0)

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" onClick={onClose}>
      <div
        className="bg-white rounded-2xl shadow-xl w-full max-w-md mx-4 flex flex-col max-h-[85vh]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 flex-shrink-0">
          <div>
            <h2 className="text-base font-bold text-gray-800">예산 관리</h2>
            {items.length > 0 && (
              <p className="text-xs text-gray-400 mt-0.5">
                총 {total.toLocaleString()}원
              </p>
            )}
          </div>
          <button onClick={onClose} className="p-1 rounded hover:bg-gray-100">
            <X size={18} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-3 space-y-2">
          {loading ? (
            <p className="text-sm text-gray-400 text-center py-4">불러오는 중...</p>
          ) : items.length === 0 && !adding ? (
            <p className="text-sm text-gray-400 text-center py-6">아직 예산 항목이 없어요</p>
          ) : items.map((item) => (
            editingId === item.id ? (
              <div key={item.id} className="flex items-center gap-2 py-2 border border-violet-200 rounded-xl px-3 bg-violet-50/40">
                <select
                  value={editState.category}
                  onChange={(e) => setEditState((s) => ({ ...s, category: e.target.value as BudgetCategory }))}
                  className="text-xs border border-gray-200 rounded-lg px-2 py-1.5 focus:outline-none focus:border-violet-400 bg-white"
                >
                  {CATEGORIES.map((c) => <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>)}
                </select>
                <input
                  type="number"
                  value={editState.amount}
                  onChange={(e) => setEditState((s) => ({ ...s, amount: e.target.value }))}
                  placeholder="금액"
                  className="w-24 border border-gray-200 rounded-lg px-2 py-1.5 text-xs focus:outline-none focus:border-violet-400"
                />
                <input
                  type="text"
                  value={editState.memo}
                  onChange={(e) => setEditState((s) => ({ ...s, memo: e.target.value }))}
                  placeholder="메모"
                  className="flex-1 border border-gray-200 rounded-lg px-2 py-1.5 text-xs focus:outline-none focus:border-violet-400"
                />
                <button onClick={() => handleEditSave(item.id)} className="p-1 rounded hover:bg-violet-100 text-violet-500">
                  <Check size={14} />
                </button>
                <button onClick={() => setEditingId(null)} className="p-1 rounded hover:bg-gray-100 text-gray-400">
                  <X size={14} />
                </button>
              </div>
            ) : (
              <div key={item.id} className="flex items-center gap-3 py-2 group">
                <span className={`text-xs font-medium px-2 py-0.5 rounded-lg flex-shrink-0 ${CATEGORY_COLOR[item.category]}`}>
                  {CATEGORY_LABEL[item.category]}
                </span>
                <div className="flex-1 min-w-0">
                  <span className="text-sm font-medium text-gray-800">{item.amount.toLocaleString()}원</span>
                  {item.memo && <span className="text-xs text-gray-400 ml-2">{item.memo}</span>}
                </div>
                <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button onClick={() => startEdit(item)} className="p-1 rounded hover:bg-gray-100 text-gray-300 hover:text-gray-500">
                    <Pencil size={13} />
                  </button>
                  <button onClick={() => handleDelete(item.id)} className="p-1 rounded hover:bg-red-50 text-gray-300 hover:text-red-400">
                    <Trash2 size={13} />
                  </button>
                </div>
              </div>
            )
          ))}

          {adding && (
            <div className="flex items-center gap-2 py-2 border border-violet-200 rounded-xl px-3 bg-violet-50/40">
              <select
                value={newItem.category}
                onChange={(e) => setNewItem((s) => ({ ...s, category: e.target.value as BudgetCategory }))}
                className="text-xs border border-gray-200 rounded-lg px-2 py-1.5 focus:outline-none focus:border-violet-400 bg-white"
              >
                {CATEGORIES.map((c) => <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>)}
              </select>
              <input
                type="number"
                value={newItem.amount}
                onChange={(e) => setNewItem((s) => ({ ...s, amount: e.target.value }))}
                placeholder="금액"
                autoFocus
                className="w-24 border border-gray-200 rounded-lg px-2 py-1.5 text-xs focus:outline-none focus:border-violet-400"
              />
              <input
                type="text"
                value={newItem.memo}
                onChange={(e) => setNewItem((s) => ({ ...s, memo: e.target.value }))}
                placeholder="메모 (선택)"
                className="flex-1 border border-gray-200 rounded-lg px-2 py-1.5 text-xs focus:outline-none focus:border-violet-400"
              />
              <button onClick={handleAdd} disabled={!newItem.amount} className="p-1 rounded hover:bg-violet-100 text-violet-500 disabled:opacity-40">
                <Check size={14} />
              </button>
              <button onClick={() => { setAdding(false); setNewItem(emptyEdit()) }} className="p-1 rounded hover:bg-gray-100 text-gray-400">
                <X size={14} />
              </button>
            </div>
          )}
        </div>

        <div className="px-6 py-3 border-t border-gray-100 flex-shrink-0">
          <button
            onClick={() => { setAdding(true); setEditingId(null) }}
            className="w-full flex items-center justify-center gap-1.5 py-2 rounded-xl border border-dashed border-gray-200 text-sm text-gray-400 hover:border-violet-300 hover:text-violet-500 transition-colors"
          >
            <Plus size={15} /> 항목 추가
          </button>
        </div>
      </div>
    </div>
  )
}

