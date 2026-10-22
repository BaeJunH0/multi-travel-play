import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios'
import toast from 'react-hot-toast'
import { useTripBlocks, lockedMessage } from './useTripBlocks'
import { blockApi } from '../services/blockApi'
import type { Block, TripEvent } from '../types'

vi.mock('../services/blockApi', () => ({
  blockApi: { moveBlock: vi.fn(), reorder: vi.fn() },
}))
vi.mock('react-hot-toast', () => ({ default: { error: vi.fn() } }))

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
// SSE는 이벤트를 하나씩 따로 전달하므로 이벤트마다 별도 act로 감싸 렌더를 나눈다
const setup = (initial: Block[]) => {
  const { result } = renderHook(() => useTripBlocks(TRIP_ID, initial))
  const emit = (...events: TripEvent[]) =>
    events.forEach((e) => act(() => result.current.handleTripEvent(e)))
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

describe('useTripBlocks.moveBlock', () => {
  const moveApi = vi.mocked(blockApi.moveBlock)
  const reorderApi = vi.mocked(blockApi.reorder)
  const toastError = vi.mocked(toast.error)

  // Day 1의 a·b 간격은 1.5e-4. c를 1.0001로 옮기면 간격이 1e-4 미만이 된다
  const a = block('a', { position: 1 })
  const b = block('b', { position: 1.00015 })

  const response = <T>(data: T, status = 200) =>
    ({ data, status, statusText: '', headers: {}, config: { headers: new AxiosHeaders() } }) as AxiosResponse<T>

  // 서버는 옮긴 블록에 요청한 위치와 version+1을 붙여 돌려준다
  const serverMoves = (moved: Block) =>
    moveApi.mockImplementation(async (_tripId, _blockId, { dayNumber, position, version }) =>
      response({ ...moved, dayNumber, position, version: version + 1 }))

  const move = async (initial: Block[], blockId: string, dayNumber: number, position: number) => {
    const { result } = setup(initial)
    await act(() => result.current.moveBlock(blockId, dayNumber, position))
    return result
  }

  beforeEach(() => {
    vi.clearAllMocks()
    reorderApi.mockResolvedValue(response(undefined))
  })

  it('#81-TC-1 이동이 저장되면 재정렬이 실패해도 이동 후 블록을 유지하고 실패 토스트를 띄우지 않는다', async () => {
    const c = block('c', { position: 3, version: 2 })
    serverMoves(c)
    reorderApi.mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'))

    const result = await move([a, b, c], 'c', 1, 1.0001)

    expect(reorderApi).toHaveBeenCalledTimes(1) // 실패하는 재정렬 경로를 실제로 탔는지
    expect(result.current.blocks.find((x) => x.id === 'c')).toEqual({ ...c, position: 1.0001, version: 3 })
    expect(toastError).not.toHaveBeenCalled()
  })

  it('#81-TC-2 409 버전 충돌이면 currentBlock으로 바꾸고 충돌 토스트를 띄우며 재정렬하지 않는다', async () => {
    const c = block('c', { position: 3, version: 2 })
    const currentC = { ...c, position: 5, version: 4 }
    moveApi.mockRejectedValue(new AxiosError('Conflict', 'ERR_BAD_REQUEST', undefined, undefined,
      response({ code: 'VERSION_CONFLICT', currentBlock: currentC }, 409)))

    const result = await move([a, b, c], 'c', 1, 1.0001)

    expect(result.current.blocks.find((x) => x.id === 'c')).toEqual(currentC)
    expect(toastError).toHaveBeenCalledWith('다른 사용자가 수정했습니다.')
    expect(reorderApi).not.toHaveBeenCalled()
  })

  it('#81-TC-3 네트워크 오류로 이동이 실패하면 이동 전 블록으로 되돌리고 이동 실패 토스트를 띄운다', async () => {
    const c = block('c', { position: 3, version: 2 })
    moveApi.mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'))

    const result = await move([a, b, c], 'c', 1, 1.0001)

    expect(result.current.blocks.find((x) => x.id === 'c')).toEqual(c)
    expect(toastError).toHaveBeenCalledWith('이동에 실패했습니다.')
    expect(reorderApi).not.toHaveBeenCalled()
  })

  it('#81-TC-4 같은 Day 안의 이동으로 간격이 처음 1e-4 미만이 되면 그 이동에서 재정렬한다', async () => {
    const c = block('c', { position: 3 })
    serverMoves(c)

    await move([a, b, c], 'c', 1, 1.0001)

    expect(reorderApi).toHaveBeenCalledTimes(1)
    expect(reorderApi).toHaveBeenCalledWith(TRIP_ID)
  })

  it('#81-TC-5 다른 Day에서 옮겨 온 블록으로 간격이 1e-4 미만이 되면 그 이동에서 재정렬한다', async () => {
    const c = block('c', { dayNumber: 2, position: 1 })
    serverMoves(c)

    await move([a, b, c], 'c', 1, 1.0001)

    expect(reorderApi).toHaveBeenCalledTimes(1)
    expect(reorderApi).toHaveBeenCalledWith(TRIP_ID)
  })

  it('#81-TC-6 이동 후에도 간격이 1e-4 이상이면 재정렬하지 않는다', async () => {
    const c = block('c', { position: 3 })
    serverMoves(c)

    await move([a, block('b', { position: 2 }), c], 'c', 1, 1.5)

    expect(reorderApi).not.toHaveBeenCalled()
  })
})
