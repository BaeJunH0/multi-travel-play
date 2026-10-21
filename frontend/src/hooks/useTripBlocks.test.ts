import { describe, it, expect } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useTripBlocks, lockedMessage } from './useTripBlocks'
import type { Block, TripEvent } from '../types'

const TRIP_ID = 'trip-1'

const block = (id: string, overrides: Partial<Block> = {}): Block => ({
  id,
  dayNumber: 1,
  position: 1,
  blockType: 'PLACE',
  placeName: `장소 ${id}`,
  lat: null,
  lng: null,
  startTime: null,
  durationMin: null,
  cost: null,
  memo: null,
  lockedBy: null,
  lockedByNickname: null,
  version: 0,
  ...overrides,
})

// 초기 블록으로 훅을 렌더링하고, SSE 이벤트를 차례로 흘려 넣는 헬퍼
const setup = (initial: Block[]) => {
  const { result } = renderHook(() => useTripBlocks(TRIP_ID, initial))
  const emit = (...events: TripEvent[]) =>
    act(() => events.forEach((e) => result.current.handleTripEvent(e)))
  return { result, emit }
}

describe('lockedMessage', () => {
  it('#82-TC-1 닉네임이 있으면 닉네임으로, 없으면 다른 사용자로 안내한다', () => {
    expect(lockedMessage('철수')).toBe('철수님이 편집 중입니다.')
    expect(lockedMessage(null)).toBe('다른 사용자가 편집 중입니다.')
  })
})

describe('useTripBlocks.handleTripEvent', () => {
  it('#82-TC-2 ADD는 새 블록을 추가하고 이미 있는 블록은 중복 추가하지 않는다', () => {
    const a = block('a')
    const b = block('b', { position: 2 })
    const { result, emit } = setup([a])

    emit(
      { action: 'ADD', tripId: TRIP_ID, payload: b },
      { action: 'ADD', tripId: TRIP_ID, payload: a },
    )

    expect(result.current.blocks.map((x) => x.id)).toEqual(['a', 'b'])
  })

  it('#82-TC-3 MOVE는 dayNumber·position·version만 바꾼다', () => {
    const a = block('a', { memo: '메모', cost: 1000 })
    const { result, emit } = setup([a])

    emit({ action: 'MOVE', tripId: TRIP_ID, payload: { blockId: 'a', dayNumber: 2, position: 1.5, version: 3 } })

    expect(result.current.blocks).toEqual([{ ...a, dayNumber: 2, position: 1.5, version: 3 }])
  })

  it('#82-TC-4 LOCK은 잠금 정보를 채우고 UNLOCK은 둘 다 null로 지운다', () => {
    const { result, emit } = setup([block('a')])

    emit({ action: 'LOCK', tripId: TRIP_ID, payload: { blockId: 'a', lockedBy: 'user-1', lockedByNickname: '철수' } })
    expect(result.current.blocks[0]).toMatchObject({ lockedBy: 'user-1', lockedByNickname: '철수' })

    emit({ action: 'UNLOCK', tripId: TRIP_ID, payload: { blockId: 'a' } })
    expect(result.current.blocks[0]).toMatchObject({ lockedBy: null, lockedByNickname: null })
  })

  it('#82-TC-5 UPDATE·CONFLICT는 블록을 교체하고 DELETE는 목록에서 뺀다', () => {
    const a = block('a')
    const b = block('b', { position: 2 })
    const { result, emit } = setup([a, b])
    const updatedA = { ...a, placeName: '수정된 장소', version: 1 }
    const currentB = { ...b, memo: '서버 최신', version: 5 }

    emit(
      { action: 'UPDATE', tripId: TRIP_ID, payload: { blockId: 'a', block: updatedA } },
      { action: 'CONFLICT', tripId: TRIP_ID, payload: { blockId: 'b', currentBlock: currentB } },
    )
    expect(result.current.blocks).toEqual([updatedA, currentB])

    emit({ action: 'DELETE', tripId: TRIP_ID, payload: { blockId: 'a' } })
    expect(result.current.blocks).toEqual([currentB])
  })
})
