import type { Presence } from '../../types'

interface Props {
  presence: Presence[]
  max?: number
}

export default function AvatarStack({ presence, max = 3 }: Props) {
  const visible = presence.slice(0, max)
  const overflow = presence.length - max

  return (
    <div className="flex items-center -space-x-2">
      {visible.map((p) => (
        <div
          key={p.userId}
          className="w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-bold ring-2 ring-white"
          style={{ background: p.avatarColor }}
          title={p.nickname}
        >
          {p.nickname[0]}
        </div>
      ))}
      {overflow > 0 && (
        <div className="w-8 h-8 rounded-full bg-gray-200 flex items-center justify-center text-gray-600 text-xs font-bold ring-2 ring-white">
          +{overflow}
        </div>
      )}
    </div>
  )
}
