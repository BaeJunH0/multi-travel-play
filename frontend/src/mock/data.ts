import type { Trip, Block, Presence, TripDay } from '../types'

export const mockTrips: Trip[] = [
  {
    id: 'trip-1',
    title: '오사카 벚꽃 여행',
    destination: '일본 오사카',
    startDate: '2026-03-28',
    endDate: '2026-04-01',

    memberCount: 3,
    myRole: 'OWNER',
  },
  {
    id: 'trip-2',
    title: '방콕 여름 휴가',
    destination: '태국 방콕',
    startDate: '2026-07-10',
    endDate: '2026-07-15',

    memberCount: 2,
    myRole: 'EDITOR',
  },
  {
    id: 'trip-3',
    title: '제주 가을 드라이브',
    destination: '대한민국 제주',
    startDate: '2026-10-03',
    endDate: '2026-10-05',

    memberCount: 4,
    myRole: 'VIEWER',
  },
]

export const mockDays: TripDay[] = [
  { dayNumber: 1, date: '2026-03-28' },
  { dayNumber: 2, date: '2026-03-29' },
  { dayNumber: 3, date: '2026-03-30' },
  { dayNumber: 4, date: '2026-03-31' },
]

export const mockBlocks: Block[] = [
  // Day 1
  { id: 'b1', dayNumber: 1, position: 0, blockType: 'HOTEL',     placeName: '도톤보리 호텔 오사카',  lat: null, lng: null, startTime: '14:00', durationMin: null,  cost: 120000, memo: '조식 포함',         lockedBy: null,    lockedByNickname: null, version: 1 },
  { id: 'b2', dayNumber: 1, position: 1, blockType: 'FOOD',      placeName: '이치란 라멘 도톤보리점', lat: null, lng: null, startTime: '18:30', durationMin: 60,    cost: 15000,  memo: null,              lockedBy: null,    lockedByNickname: null, version: 1 },
  { id: 'b3', dayNumber: 1, position: 2, blockType: 'PLACE',     placeName: '도톤보리 거리',          lat: null, lng: null, startTime: '20:00', durationMin: 90,    cost: null,   memo: '야경 포토스팟',    lockedBy: 'u2',    lockedByNickname: '지민', version: 1 },
  // Day 2
  { id: 'b4', dayNumber: 2, position: 0, blockType: 'TRANSPORT', placeName: '오사카 → 교토 (신칸센)', lat: null, lng: null, startTime: '09:00', durationMin: 15,    cost: 2900,   memo: null,              lockedBy: null,    lockedByNickname: null, version: 1 },
  { id: 'b5', dayNumber: 2, position: 1, blockType: 'PLACE',     placeName: '후시미 이나리 신사',      lat: null, lng: null, startTime: '10:00', durationMin: 120,   cost: null,   memo: '아침 일찍 방문 추천', lockedBy: null, lockedByNickname: null, version: 1 },
  { id: 'b6', dayNumber: 2, position: 2, blockType: 'CAFE',      placeName: '% 아라비카 교토',         lat: null, lng: null, startTime: '13:00', durationMin: 45,    cost: 8000,   memo: null,              lockedBy: null,    lockedByNickname: null, version: 1 },
  { id: 'b7', dayNumber: 2, position: 3, blockType: 'FOOD',      placeName: '기온 오카베',              lat: null, lng: null, startTime: '19:00', durationMin: 90,    cost: 45000,  memo: '예약 필수',       lockedBy: null,    lockedByNickname: null, version: 1 },
  // Day 3
  { id: 'b8', dayNumber: 3, position: 0, blockType: 'PLACE',     placeName: '오사카 성',               lat: null, lng: null, startTime: '09:30', durationMin: 120,   cost: 6000,   memo: null,              lockedBy: null,    lockedByNickname: null, version: 1 },
  { id: 'b9', dayNumber: 3, position: 1, blockType: 'FOOD',      placeName: '쿠로몬 시장',              lat: null, lng: null, startTime: '12:00', durationMin: 60,    cost: 20000,  memo: '해산물 추천',     lockedBy: null,    lockedByNickname: null, version: 1 },
  // Day 4
  { id: 'b10', dayNumber: 4, position: 0, blockType: 'CAFE',     placeName: '스타벅스 오사카 나카지마', lat: null, lng: null, startTime: '09:00', durationMin: 60,    cost: 7000,   memo: null,              lockedBy: null,    lockedByNickname: null, version: 1 },
  { id: 'b11', dayNumber: 4, position: 1, blockType: 'TRANSPORT', placeName: '간사이 공항 이동',         lat: null, lng: null, startTime: '14:00', durationMin: 60,    cost: 1400,   memo: '하루카 특급',     lockedBy: null,    lockedByNickname: null, version: 1 },
]

export const mockPresence: Presence[] = [
  { userId: 'u1', nickname: '나', avatarColor: '#7F77DD' },
  { userId: 'u2', nickname: '지민', avatarColor: '#D85A30' },
  { userId: 'u3', nickname: '현우', avatarColor: '#1D9E75' },
]
