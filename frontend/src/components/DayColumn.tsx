import { useState } from 'react'
import { Plus, Trash2, ArrowDownWideNarrow } from 'lucide-react'
import type { Block, TripDay, BlockType } from '../types'
import BlockCard from './BlockCard'

interface Props {
  day: TripDay
  blocks: Block[]
  isViewer?: boolean
  canDelete?: boolean
  draggedBlockId: string | null
  dragTargetInfo: { blockId: string; pos: 'above' | 'below' } | null
  onAddBlock: (dayNumber: number, type?: BlockType) => void
  onDeleteDay: (dayNumber: number) => void
  onChangeDate: (dayNumber: number, newDate: string) => void
  onSortByTime: (dayNumber: number) => void
  onEditBlock: (block: Block) => void
  onDeleteBlock: (blockId: string) => void
  onDragStart: (e: React.DragEvent, block: Block) => void
  onDragEnd: (e: React.DragEvent) => void
  onDragOverBlock: (e: React.DragEvent, blockId: string) => void
  onDropOnBlock: (e: React.DragEvent, targetBlockId: string) => void
  onDropOnColumn: (e: React.DragEvent, dayNumber: number) => void
}

const DATE_LABELS = ['일', '월', '화', '수', '목', '금', '토']

function formatDate(dateStr: string) {
  const d = new Date(dateStr)
  const month = d.getMonth() + 1
  const day = d.getDate()
  const dow = DATE_LABELS[d.getDay()]
  return `${month}월 ${day}일 (${dow})`
}

export default function DayColumn({
  day,
  blocks,
  isViewer = false,
  canDelete = true,
  draggedBlockId,
  dragTargetInfo,
  onAddBlock,
  onDeleteDay,
  onChangeDate,
  onSortByTime,
  onEditBlock,
  onDeleteBlock,
  onDragStart,
  onDragEnd,
  onDragOverBlock,
  onDropOnBlock,
  onDropOnColumn,
}: Props) {
  const [isDragOver, setIsDragOver] = useState(false)
  const [editingDate, setEditingDate] = useState(false)

  const sorted = [...blocks].sort((a, b) => a.position - b.position)

  const handleDateChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newDate = e.target.value
    if (newDate) onChangeDate(day.dayNumber, newDate)
    setEditingDate(false)
  }

  return (
    <div
      className="flex-shrink-0 w-64 flex flex-col rounded-2xl bg-white shadow-sm"
      onDragOver={(e) => {
        e.preventDefault()
        setIsDragOver(true)
      }}
      onDragLeave={() => setIsDragOver(false)}
      onDrop={(e) => {
        setIsDragOver(false)
        onDropOnColumn(e, day.dayNumber)
      }}
      style={{
        outline: isDragOver ? '2px dashed #7F77DD' : '2px solid transparent',
      }}
    >
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
        <div className="flex items-center gap-1.5 min-w-0">
          <span className="font-bold text-gray-800 text-sm flex-shrink-0">Day {day.dayNumber}</span>
          {!isViewer && editingDate ? (
            <input
              type="date"
              defaultValue={day.date}
              autoFocus
              onChange={handleDateChange}
              onBlur={() => setEditingDate(false)}
              className="text-xs border border-violet-300 rounded px-1.5 py-0.5 focus:outline-none focus:border-violet-500 w-32"
            />
          ) : (
            <button
              onClick={() => !isViewer && setEditingDate(true)}
              className={`text-xs text-gray-400 truncate ${!isViewer ? 'hover:text-violet-500 hover:underline cursor-pointer' : 'cursor-default'}`}
            >
              {formatDate(day.date)}
            </button>
          )}
        </div>
        {!isViewer && (
          <div className="flex items-center gap-1">
            <button
              onClick={() => onSortByTime(day.dayNumber)}
              title="시간순 정렬"
              className="w-6 h-6 rounded-full bg-gray-100 hover:bg-violet-100 hover:text-violet-600 flex items-center justify-center transition-colors"
            >
              <ArrowDownWideNarrow size={14} />
            </button>
            <button
              onClick={() => onAddBlock(day.dayNumber)}
              className="w-6 h-6 rounded-full bg-gray-100 hover:bg-violet-100 hover:text-violet-600 flex items-center justify-center transition-colors"
            >
              <Plus size={14} />
            </button>
            {canDelete && (
              <button
                onClick={() => onDeleteDay(day.dayNumber)}
                className="w-6 h-6 rounded-full bg-gray-100 hover:bg-red-100 hover:text-red-500 flex items-center justify-center transition-colors"
              >
                <Trash2 size={12} />
              </button>
            )}
          </div>
        )}
      </div>

      {/* Blocks */}
      <div className="flex-1 py-2 min-h-20">
        {sorted.length === 0 && (
          <p className="text-center text-xs text-gray-300 py-6">블록을 추가해 보세요</p>
        )}
        {sorted.map((block, idx) => (
          <div key={block.id}>
            {idx > 0 && (
              <div className="flex justify-center">
                <div className="w-px h-3 bg-gray-200" />
              </div>
            )}
            <BlockCard
              block={block}
              isViewer={isViewer}
              isDragging={draggedBlockId === block.id}
              dragTargetPos={dragTargetInfo?.blockId === block.id ? dragTargetInfo.pos : null}
              onEdit={onEditBlock}
              onDelete={onDeleteBlock}
              onDragStart={(e) => onDragStart(e, block)}
              onDragEnd={onDragEnd}
              onDragOver={(e) => onDragOverBlock(e, block.id)}
              onDrop={(e) => onDropOnBlock(e, block.id)}
            />
          </div>
        ))}
      </div>
    </div>
  )
}
