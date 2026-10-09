package com.bossxor.lottegiants.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PlayerBiographyTest {
    @Test fun ageUsesBirthdayAndSupportsCompactDate() {
        assertEquals(37, playerAge("19880102", LocalDate.of(2026, 1, 1)))
        assertEquals(38, playerAge("1988-01-02", LocalDate.of(2026, 1, 2)))
        assertNull(playerAge("잘못된 날짜"))
        assertNull(playerAge("20260102", LocalDate.of(2026, 1, 1)))
    }
    @Test fun schoolsDoNotIncludeTeams() {
        assertEquals(listOf("신자초(자이언츠리틀)", "자양중", "신일고", "방송통신대"),
            schoolNames("신자초(자이언츠리틀)-자양중-신일고-(방송통신대)-삼성-상무-SK-SSG"))
    }
    @Test fun naverProfileRequiresCorrectIdentityAndPreservesPeriods() {
        val html = """<section class="_au_people_content_wrap"><a href="?os=124753&amp;x=1" data-title="김태혁"></a>
            <div class="cm_content_area _cm_content_area_profile"><dl><dt>출생</dt><dd>1988.01.02. 대전광역시</dd></dl></div>
            <div class="cm_content_area _cm_content_area_school"><dl><span class="text">신일고등학교</span></dl></div>
            <div class="cm_content_area _cm_content_area_career"><dl>
            <div class="info_group"><dt><span></span>2022.11.~</dt><dd><span class="text">롯데 자이언츠</span></dd></div>
            <div class="info_group last"><dt>2006~2009.12.</dt><dd><span class="text">삼성 라이온즈</span></dd></div>
            </dl></div></section>"""
        val p = parseNaverBiography(html, "김태혁", "1988-01-02", "124753")!!
        assertEquals(listOf("신일고등학교"), p.education)
        assertEquals("2022.11.~", p.careers.first().period)
        assertEquals("삼성 라이온즈", p.careers.last().team)
        assertNull(parseNaverBiography(html, "김태형", "1988-01-02", "124753"))
        assertNull(parseNaverBiography(html, "김태혁", "1988-01-03", "124753"))
        assertNull(parseNaverBiography(html, "김태혁", "1988-01-02", "123"))
    }
    @Test fun kboProfileIsMatchedByName() {
        val html = """<span id="x_playerProfile_lblName">김태혁</span>
            <span id="x_playerProfile_lblBirthday">1988년 01월 02일</span>
            <span id="x_playerProfile_lblCareer">신자초-자양중-신일고-삼성</span>"""
        assertEquals("1988-01-02", parseKboBiography(html, "김태혁")!!.birth)
        assertEquals(3, parseKboBiography(html, "김태혁")!!.education.size)
        assertNull(parseKboBiography(html, "김태형"))
    }
}
