package demo.travel.block

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime
import java.util.*

interface BlockRepository : JpaRepository<ScheduleBlock, UUID> {
    fun findAllByTripIdOrderByDayNumberAscPositionAsc(tripId: UUID): List<ScheduleBlock>
    fun findTopByTripIdAndDayNumberOrderByPositionDesc(tripId: UUID, dayNumber: Int): ScheduleBlock?
    fun findAllByTripIdAndLockedById(tripId: UUID, userId: UUID): List<ScheduleBlock>
    fun findAllByLockedByIsNotNull(): List<ScheduleBlock>

    // 아래 잠금 갱신은 heartbeat·연결 종료 처리에서 쓴다. 읽은 뒤 다른 요청이 블록을 바꿨을 수 있으므로
    // 엔티티 변경(version 확인) 대신 잠금 컬럼만 바꾸고, 조건을 WHERE에서 다시 확인한다. 반환값은 바뀐 행 수(0 또는 1).

    @Modifying
    @Query("UPDATE ScheduleBlock b SET b.lockExpiresAt = :expiresAt WHERE b.id = :blockId AND b.lockedBy.id = :userId")
    fun extendLock(blockId: UUID, userId: UUID, expiresAt: LocalDateTime): Int

    @Modifying
    @Query("UPDATE ScheduleBlock b SET b.lockedBy = NULL, b.lockExpiresAt = NULL WHERE b.id = :blockId AND b.lockedBy.id = :userId")
    fun releaseLock(blockId: UUID, userId: UUID): Int

    @Modifying
    @Query(
        "UPDATE ScheduleBlock b SET b.lockedBy = NULL, b.lockExpiresAt = NULL " +
            "WHERE b.id = :blockId AND b.lockedBy IS NOT NULL AND (b.lockExpiresAt IS NULL OR b.lockExpiresAt <= :now)"
    )
    fun releaseExpiredLock(blockId: UUID, now: LocalDateTime): Int
}
