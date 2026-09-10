package com.bossxor.lottegiants.domain

object LotteHistory {
    val sections: List<TeamHistorySection>
        get() = TeamHistory.byCode(LOTTE_TEAM_CODE)
}

object TeamHistory {
    fun byCode(code: String): List<TeamHistorySection> = when (normalizeTeamCode(code)) {
        "LT" -> lotte
        "OB" -> doosan
        "LG" -> lg
        "SS" -> samsung
        "HH" -> hanwha
        "KT" -> kt
        "HT" -> kia
        "NC" -> nc
        "SK" -> ssg
        "WO" -> kiwoom
        else -> lotte
    }

    private val lotte = listOf(
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

    private val doosan = brief(
        overview = listOf("1982 OB 베어스 창단 · 1999 두산 베어스", "연고지 서울 · 홈구장 잠실야구장", "팀컬러 네이비"),
        titles = listOf("한국시리즈 우승 6회 (1982, 1995, 2001, 2015, 2016, 2019)", "준우승 다수 · 2015~2016 2연패"),
        retired = listOf("21번 박철순 · 54번 김영신 등"),
        park = listOf("서울 잠실야구장 · 지하철 2·8호선 잠실역", "응원석은 주로 1루 측"),
        cheer = listOf("대표 응원가 〈최강 두산〉", "잠실 홈 팬덤"),
    )

    private val lg = brief(
        overview = listOf("1990 MBC 청룡 인수 · LG 트윈스", "연고지 서울 · 홈구장 잠실야구장", "팀컬러 와인 레드"),
        titles = listOf("한국시리즈 우승 3회 (1990, 1994, 2023)", "1990 창단 첫해 우승"),
        retired = listOf("41번 김용수 등"),
        park = listOf("서울 잠실야구장 · 두산과 공동 홈", "응원석은 주로 1루 측"),
        cheer = listOf("대표 응원가 〈LG 트윈스〉", "잠실 홈 팬덤"),
    )

    private val samsung = brief(
        overview = listOf("1982 삼성 라이온즈 창단", "연고지 대구 · 홈구장 라이온즈파크", "팀컬러 블루"),
        titles = listOf("한국시리즈 우승 8회 (1985, 2002, 2005, 2006, 2011~2014)", "2011~2014 4연패"),
        retired = listOf("22번 이승엽 · 36번 양준혁 등"),
        park = listOf("대구 삼성 라이온즈파크", "지하철 2호선 대공원역"),
        cheer = listOf("대표 응원가 〈승리의 노래〉", "대구·경북 연고"),
    )

    private val hanwha = brief(
        overview = listOf("1986 빙그레 이글스 · 1993 한화 이글스", "연고지 대전 · 홈구장 한화생명이글스파크", "팀컬러 오렌지"),
        titles = listOf("한국시리즈 우승 1회 (1999)", "준우승 다수"),
        retired = listOf("23번 정민철 · 35번 장종훈 등"),
        park = listOf("대전 한화생명이글스파크", "서구 괴정동 일대"),
        cheer = listOf("대표 응원가 〈최강 한화〉", "대전·충청 연고"),
    )

    private val kt = brief(
        overview = listOf("2013 창단 · 2015 1군 진입", "연고지 수원 · 홈구장 KT위즈파크", "팀컬러 블랙·레드"),
        titles = listOf("한국시리즈 우승 1회 (2021)"),
        retired = listOf("영구결번은 구단 안내에 따름"),
        park = listOf("수원 KT위즈파크", "장안구 조원동"),
        cheer = listOf("위즈 응원가 · 수원 홈 팬덤"),
    )

    private val kia = brief(
        overview = listOf("1982 해태 타이거즈 · 2001 KIA 타이거즈", "연고지 광주 · 홈구장 챔피언스필드", "팀컬러 레드"),
        titles = listOf("한국시리즈 우승 12회 (최다)", "해태 시절 9회 + KIA 3회"),
        retired = listOf("7번 이종범 · 18번 선동열 등"),
        park = listOf("광주-기아 챔피언스필드", "북구 임동"),
        cheer = listOf("대표 응원가 〈최강 기아〉", "광주·전남 연고"),
    )

    private val nc = brief(
        overview = listOf("2011 창단 · 2013 1군 진입", "연고지 창원 · 홈구장 NC파크", "팀컬러 네이비·골드"),
        titles = listOf("한국시리즈 우승 1회 (2020)"),
        retired = listOf("영구결번은 구단 안내에 따름"),
        park = listOf("창원 NC파크", "마산회원구 양덕동"),
        cheer = listOf("다이노스 응원가 · 경남 연고"),
    )

    private val ssg = brief(
        overview = listOf("2000 SK 와이번스 · 2021 SSG 랜더스", "연고지 인천 · 홈구장 랜더스필드", "팀컬러 레드"),
        titles = listOf("한국시리즈 우승 5회 (2007, 2008, 2010, 2018, 2022)"),
        retired = listOf("21번 박경완 등"),
        park = listOf("인천 SSG 랜더스필드(문학)", "미추홀구 문학동"),
        cheer = listOf("랜더스 응원가 · 인천 연고"),
    )

    private val kiwoom = brief(
        overview = listOf("2008 히어로즈 · 2019 키움 히어로즈", "연고지 서울 · 홈구장 고척스카이돔", "팀컬러 마룬"),
        titles = listOf("한국시리즈 준우승 (2014, 2019 등)"),
        retired = listOf("영구결번은 구단 안내에 따름"),
        park = listOf("서울 고척스카이돔", "구로구 고척동 · 돔구장"),
        cheer = listOf("히어로즈 응원가 · 고척 홈 팬덤"),
    )

    private fun brief(
        overview: List<String>,
        titles: List<String>,
        retired: List<String>,
        park: List<String>,
        cheer: List<String>,
    ): List<TeamHistorySection> = listOf(
        TeamHistorySection("구단 개요", overview),
        TeamHistorySection("한국시리즈", titles),
        TeamHistorySection("영구결번", retired),
        TeamHistorySection("홈구장 가이드", park),
        TeamHistorySection("상징 · 응원", cheer),
    )
}
