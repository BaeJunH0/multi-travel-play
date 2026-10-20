package demo.travel.block.presentation.dto

import demo.travel.block.application.VersionConflictException

/** 409 VERSION_CONFLICT 응답. 표준 에러 형태(code, message)에 서버의 최신 블록을 더한다. */
data class VersionConflictResponse(val code: String, val message: String?, val currentBlock: BlockResponse) {
    companion object {
        fun of(e: VersionConflictException) = VersionConflictResponse(
            code = "VERSION_CONFLICT",
            message = e.message,
            currentBlock = BlockResponse.of(e.currentBlock),
        )
    }
}
