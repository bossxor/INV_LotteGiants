package com.bossxor.lottegiants.domain

/** KBO 구장 좌표 (구장 날씨용) */
data class StadiumCoord(val name: String, val lat: Double, val lon: Double)

private val STADIUMS = listOf(
    StadiumCoord("사직", 35.1941, 129.0616),
    StadiumCoord("부산", 35.1941, 129.0616),
    StadiumCoord("잠실", 37.5121, 127.0719),
    StadiumCoord("고척", 37.4982, 126.8671),
    StadiumCoord("문학", 37.4370, 126.6933),
    StadiumCoord("인천", 37.4370, 126.6933),
    StadiumCoord("수원", 37.2997, 127.0097),
    StadiumCoord("대전", 36.3171, 127.4291),
    StadiumCoord("광주", 35.1681, 126.8891),
    StadiumCoord("대구", 35.8410, 128.6816),
    StadiumCoord("창원", 35.2225, 128.5794),
    StadiumCoord("울산", 35.5322, 129.2595),
    StadiumCoord("청주", 36.6386, 127.4850),
    StadiumCoord("포항", 36.0084, 129.3590),
)

fun resolveStadiumCoord(stadium: String): StadiumCoord {
    val key = stadium.trim()
    return STADIUMS.firstOrNull { key.contains(it.name) } ?: StadiumCoord(key.ifBlank { "사직" }, 35.1941, 129.0616)
}

fun weatherSummaryKo(code: Int): String = when (code) {
    0 -> "맑음"
    1, 2 -> "대체로 맑음"
    3 -> "흐림"
    45, 48 -> "안개"
    51, 53, 55 -> "이슬비"
    56, 57 -> "어는 이슬비"
    61, 63, 65 -> "비"
    66, 67 -> "어는 비"
    71, 73, 75, 77 -> "눈"
    80, 81, 82 -> "소나기"
    85, 86 -> "소낙눈"
    95 -> "뇌우"
    96, 99 -> "뇌우·우박"
    else -> "보통"
}
