import type { BlockType } from '../../types'
import { BLOCK_STYLE } from '../../types'

interface Props {
  type: BlockType
  size?: 'sm' | 'md'
}

export default function BlockTypeBadge({ type, size = 'sm' }: Props) {
  const s = BLOCK_STYLE[type]
  const px = size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-3 py-1 text-sm'
  return (
    <span
      className={`inline-block rounded-full font-medium ${px}`}
      style={{ background: s.badge, color: s.border }}
    >
      {s.label}
    </span>
  )
}
