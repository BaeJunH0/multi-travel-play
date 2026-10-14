import { useState, useCallback } from 'react'
import toast from 'react-hot-toast'
import { isAxiosError } from 'axios'
import { blockApi } from '../services/blockApi'
import type { Block, BlockType, WsMessage } from '../types'

export function useTripBlocks(tripId: string, initial: Block[]) {
  const [blocks, setBlocks] = useState<Block[]>(initial)

  // WebSocket 이벤트 처리
  const handleWsMessage = useCallback((msg: WsMessage) => {
    const p = msg.payload as any
    switch (msg.action) {
      case 'ADD':
        setBlocks((prev) => prev.some((b) => b.id === p.id) ? prev : [...prev, p])
        break
      case 'UPDATE':
        setBlocks((prev) => prev.map((b) => b.id === p.blockId ? p.block : b))
        break
      case 'MOVE':
        setBlocks((prev) => prev.map((b) =>
          b.id === p.blockId
            ? { ...b, dayNumber: p.dayNumber, position: p.position, version: p.version }
            : b
        ))
        break
      case 'DELETE':
        setBlocks((prev) => prev.filter((b) => b.id !== p.blockId))
        break
      case 'LOCK':
        setBlocks((prev) => prev.map((b) =>
          b.id === p.blockId
            ? { ...b, lockedBy: p.lockedBy, lockedByNickname: p.lockedByNickname }
            : b
        ))
        break
      case 'UNLOCK':
        setBlocks((prev) => prev.map((b) =>
          b.id === p.blockId ? { ...b, lockedBy: null, lockedByNickname: null } : b
        ))
        break
      case 'CONFLICT':
        // 요청자에게는 409 응답에서 이미 토스트를 띄우므로 여기서는 상태만 동기화한다
        setBlocks((prev) => prev.map((b) => b.id === p.blockId ? p.currentBlock : b))
        break
    }
  }, [])

  // 블록 추가
  const addBlock = useCallback(async (data: {
    dayNumber: number; blockType: BlockType; placeName: string
    startTime: string | null; durationMin: number | null; cost: number | null; memo: string | null
  }) => {
    const { data: newBlock } = await blockApi.addBlock(tripId, data)
    setBlocks((prev) => prev.some((b) => b.id === newBlock.id) ? prev : [...prev, newBlock])
    return newBlock
  }, [tripId])

  // 블록 수정 (Optimistic)
  const updateBlock = useCallback(async (blockId: string, patch: Partial<Block>) => {
    const prev = blocks.find((b) => b.id === blockId)!
    setBlocks((cur) => cur.map((b) => b.id === blockId ? { ...b, ...patch } : b))
    try {
      const { data: updated } = await blockApi.updateBlock(tripId, blockId, { ...patch, version: prev.version })
      setBlocks((cur) => cur.map((b) => b.id === blockId ? updated : b))
    } catch (err: any) {
      if (err.response?.data?.code === 'VERSION_CONFLICT') {
        const latest: Block = err.response.data.currentBlock ?? prev
        setBlocks((cur) => cur.map((b) => b.id === blockId ? latest : b))
        toast.error('다른 사용자가 수정했습니다. 최신 내용으로 되돌렸어요.')
      } else {
        setBlocks((cur) => cur.map((b) => b.id === blockId ? prev : b))
        toast.error('저장에 실패했습니다.')
      }
    }
  }, [tripId, blocks])

  // 블록 이동 (Optimistic)
  const moveBlock = useCallback(async (blockId: string, dayNumber: number, position: number) => {
    const prev = blocks.find((b) => b.id === blockId)!
    setBlocks((cur) => cur.map((b) =>
      b.id === blockId ? { ...b, dayNumber, position } : b
    ))
    try {
      const { data: updated } = await blockApi.moveBlock(tripId, blockId, { dayNumber, position, version: prev.version })
      setBlocks((cur) => cur.map((b) => b.id === blockId ? updated : b))
      // position gap이 너무 작으면 reorder
      const dayBlocks = blocks.filter((b) => b.dayNumber === dayNumber).sort((a, b) => a.position - b.position)
      const minGap = dayBlocks.reduce((min, b, i) =>
        i === 0 ? min : Math.min(min, b.position - dayBlocks[i - 1].position), Infinity)
      if (minGap < 1e-4) await blockApi.reorder(tripId)
    } catch (err: any) {
      if (err.response?.data?.code === 'VERSION_CONFLICT') {
        const latest: Block = err.response.data.currentBlock ?? prev
        setBlocks((cur) => cur.map((b) => b.id === blockId ? latest : b))
        toast.error('다른 사용자가 수정했습니다.')
      } else if (err.response?.status === 423) {
        setBlocks((cur) => cur.map((b) => b.id === blockId ? prev : b))
        toast.error(lockedMessage(prev.lockedByNickname))
      } else {
        setBlocks((cur) => cur.map((b) => b.id === blockId ? prev : b))
        toast.error('이동에 실패했습니다.')
      }
    }
  }, [tripId, blocks])

  // 블록 삭제
  const deleteBlock = useCallback(async (blockId: string) => {
    const prev = [...blocks]
    setBlocks((cur) => cur.filter((b) => b.id !== blockId))
    try {
      await blockApi.deleteBlock(tripId, blockId)
    } catch (err) {
      setBlocks(prev)
      if (isAxiosError(err) && err.response?.status === 423) {
        toast.error(lockedMessage(prev.find((b) => b.id === blockId)?.lockedByNickname ?? null))
      } else {
        toast.error('삭제에 실패했습니다.')
      }
    }
  }, [tripId, blocks])

  // 잠금 획득 / 해제
  const lockBlock = useCallback((blockId: string) =>
    blockApi.lockBlock(tripId, blockId), [tripId])

  const unlockBlock = useCallback((blockId: string) =>
    blockApi.unlockBlock(tripId, blockId), [tripId])

  return { blocks, setBlocks, handleWsMessage, addBlock, updateBlock, moveBlock, deleteBlock, lockBlock, unlockBlock }
}

// 423 ALREADY_LOCKED 안내 문구
export const lockedMessage = (nickname: string | null) =>
  nickname ? `${nickname}님이 편집 중입니다.` : '다른 사용자가 편집 중입니다.'
