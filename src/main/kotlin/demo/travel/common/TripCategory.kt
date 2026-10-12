package demo.travel.common

enum class TripCategory {
    HOTEL, FOOD, CAFE, PLACE, TRANSPORT, FLIGHT, ETC;

    /** 일정 블록에 쓸 수 있는 카테고리인지. FLIGHT, ETC는 budget 전용. */
    val isBlockType: Boolean
        get() = this in BLOCK_TYPES

    companion object {
        private val BLOCK_TYPES = setOf(HOTEL, FOOD, CAFE, PLACE, TRANSPORT)
    }
}
