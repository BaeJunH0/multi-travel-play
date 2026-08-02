package demo.travel.common.exception

import demo.travel.block.application.dto.BlockResult

class VersionConflictException(val currentBlock: BlockResult) :
    RuntimeException("다른 사용자가 이미 수정했습니다.")
