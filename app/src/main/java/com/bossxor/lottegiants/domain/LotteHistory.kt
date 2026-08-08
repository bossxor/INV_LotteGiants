package com.bossxor.lottegiants.domain

object LotteHistory {
    val sections: List<TeamHistorySection> = listOf(
        TeamHistorySection(
            title = "구단 개요",
            items = listOf(
                "1975 실업 야구단 창단 · 1982 프로 전환",
                "연고지 부산광역시 · 홈구장 사직야구장",
                "별칭 부산 갈매기 · 팀컬러 네이비·레드·골드",
            ),
        ),
        TeamHistorySection(
            title = "한국시리즈 우승 (2회)",
            items = listOf(
                "1984 삼성 라이온즈 상대로 4승 3패 (창단 첫 우승, MVP 유두열)",
                "1992 빙그레 이글스 상대로 4승 1패 (정규시즌 3위 → 우승, MVP 박동희)",
            ),
        ),
        TeamHistorySection(
            title = "한국시리즈 준우승",
            items = listOf(
                "1995 OB 베어스 (3승 4패)",
                "1999 한화 이글스 (1승 4패)",
            ),
        ),
        TeamHistorySection(
            title = "영구결번",
            items = listOf(
                "10번 이대호 (2022.10.8 영구결번식)",
                "11번 최동원 (2011.9.30 영구결번식 · 구단 최초)",
            ),
        ),
        TeamHistorySection(
            title = "사직 구장 가이드",
            items = listOf(
                "부산 동래구 사직로 45 · 사직야구장",
                "지하철 1·3호선 연산역 · 버스 사직운동장 하차",
                "응원석(응원단)은 주로 1루 측 · 원정 응원은 3루 측",
                "우천 시 그라운드 상태·중계 안내를 앱에서 확인",
            ),
        ),
        TeamHistorySection(
            title = "상징 · 응원",
            items = listOf(
                "대표 응원가 〈부산갈매기〉",
                "사직 홈 팬덤 · 부산·경남 연고",
            ),
        ),
        TeamHistorySection(
            title = "주요 기록",
            items = listOf(
                "1984 후기리그 우승 후 한국시리즈 우승",
                "1991 프로스포츠 최초 홈 100만 관중",
                "2008~2012 5년 연속 포스트시즌 진출",
            ),
        ),
    )
}
