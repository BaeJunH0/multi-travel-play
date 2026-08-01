package demo.travel.common.exception

import demo.travel.block.dto.BlockResponse

class VersionConflictException(val currentBlock: BlockResponse) :
    RuntimeException("다른 사용자가 이미 수정했습니다.")
