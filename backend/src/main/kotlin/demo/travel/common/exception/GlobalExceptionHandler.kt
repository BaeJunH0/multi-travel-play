package demo.travel.common.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(e: ResponseStatusException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(e.statusCode)
            .body(ErrorResponse(e.statusCode.value().toErrorCode(), e.reason ?: e.message))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("INVALID_REQUEST", "요청 본문이 올바르지 않습니다."))

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(e: BusinessException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(e.status)
            .body(ErrorResponse(e.code, e.message))

    @ExceptionHandler(VersionConflictException::class)
    fun handleVersionConflict(e: VersionConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(mapOf("code" to "VERSION_CONFLICT", "message" to e.message, "currentBlock" to e.currentBlock))
}

data class ErrorResponse(val code: String, val message: String?)

private fun Int.toErrorCode() = when (this) {
    400 -> "INVALID_REQUEST"
    401 -> "UNAUTHORIZED"
    403 -> "FORBIDDEN"
    404 -> "NOT_FOUND"
    409 -> "CONFLICT"
    423 -> "ALREADY_LOCKED"
    else -> "INTERNAL_ERROR"
}
