import { Trash2, Clock, Timer, Coins } from 'lucide-react'
import type { Block } from '../types'
import { BLOCK_STYLE } from '../types'
import BlockTypeBadge from './ui/BlockTypeBadge'

interface Props {
  block: Block
  onEdit: (block: Block) => void
  onDelete: (blockId: string) => void
  isViewer?: boolean
  isDragging?: boolean
  dragTargetPos?: 'above' | 'below' | null
  onDragStart?: (e: React.DragEvent) => void
  onDragEnd?: (e: React.DragEvent) => void
  onDragOver?: (e: React.DragEvent) => void
  onDrop?: (e: React.DragEvent) => void
}

export default function BlockCard({
  block,
  onEdit,
  onDelete,
  isViewer = false,
  isDragging = false,
  dragTargetPos = null,
  onDragStart,
  onDragEnd,
  onDragOver,
  onDrop,
}: Props) {
  const s = BLOCK_STYLE[block.blockType]
  const locked = block.lockedBy !== null
  const editable = !locked && !isViewer

  const handleCardClick = () => {
    if (editable) onEdit(block)
  }

  const handleDeleteClick = (e: React.MouseEvent) => {
    e.stopPropagation()
    onDelete(block.id)
  }

  return (
    <div className="relative">
      {dragTargetPos === 'above' && (
        <div className="h-0.5 bg-violet-500 rounded mx-2 mb-1" />
      )}

      <div
        draggable={editable}
        onDragStart={onDragStart}
        onDragEnd={onDragEnd}
        onDragOver={onDragOver}
        onDrop={onDrop}
        onClick={handleCardClick}
        className="relative rounded-xl mx-2 mb-1 overflow-hidden transition-opacity"
        style={{
          background: s.bg,
          borderLeft: `3px solid ${s.border}`,
          opacity: isDragging ? 0.35 : 1,
          outline: locked ? `1.5px solid #D4537E` : 'none',
          cursor: editable ? 'pointer' : 'default',
        }}
      >
        <div className="flex items-start gap-2 px-3 py-2.5">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-1.5 flex-wrap mb-1">
              <BlockTypeBadge type={block.blockType} />
              {locked && (
                <span className="inline-block px-2 py-0.5 text-xs rounded-full bg-pink-100 text-pink-600 font-medium">
                  {block.lockedByNickname} 편집 중
                </span>
              )}
            </div>
            <p className="text-sm font-semibold text-gray-800 truncate">{block.placeName}</p>
            <div className="flex items-center gap-3 mt-1 flex-wrap">
              {block.startTime && (
                <span className="flex items-center gap-1 text-xs text-gray-500">
                  <Clock size={11} /> {block.startTime}
                </span>
              )}
              {block.durationMin && (
                <span className="flex items-center gap-1 text-xs text-gray-500">
                  <Timer size={11} /> {block.durationMin}분
                </span>
              )}
              {block.cost != null && (
                <span className="flex items-center gap-1 text-xs text-gray-500">
                  <Coins size={11} /> {block.cost.toLocaleString()}원
                </span>
              )}
            </div>
            {block.memo && (
              <p className="text-xs text-gray-400 mt-1 truncate">{block.memo}</p>
            )}
          </div>

          {editable && (
            <button
              onClick={handleDeleteClick}
              className="p-1 rounded hover:bg-black/10 text-gray-300 hover:text-red-400 transition-colors flex-shrink-0 self-center"
            >
              <Trash2 size={14} />
            </button>
          )}
        </div>
      </div>

      {dragTargetPos === 'below' && (
        <div className="h-0.5 bg-violet-500 rounded mx-2 mt-1" />
      )}
    </div>
  )
}
