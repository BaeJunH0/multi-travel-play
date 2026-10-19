package demo.travel.block.presentation

import demo.travel.block.application.VersionConflictException
import demo.travel.block.presentation.dto.VersionConflictResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** block 도메인 전용 예외 응답. 공통 핸들러(common)가 도메인 타입을 알지 않도록 여기서 처리한다. */
@RestControllerAdvice
class BlockExceptionHandler {

    @ExceptionHandler(VersionConflictException::class)
    fun handleVersionConflict(e: VersionConflictException): ResponseEntity<VersionConflictResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(VersionConflictResponse.of(e))
}
