package demo.travel.common.exception

import org.springframework.http.HttpStatus

class BusinessException(
    val code: String,
    override val message: String,
    val status: HttpStatus = HttpStatus.BAD_REQUEST,
) : RuntimeException(message)
