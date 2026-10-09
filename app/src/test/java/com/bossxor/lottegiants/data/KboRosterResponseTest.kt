package com.bossxor.lottegiants.data

import org.junit.Assert.*
import org.junit.Test

class KboRosterResponseTest {
    private val empty = """{"rows":[]}"""
    @Test fun successfulEmptyPublicationIsValid() {
        val result = KboRosterParser.parseConfirmed(KboRosterResponse(empty, empty, "100"))
        assertTrue(result.first.isEmpty())
        assertTrue(result.second.isEmpty())
    }
    @Test fun upstreamFailureIsNotAnEmptyPublication() {
        assertThrows(IllegalStateException::class.java) {
            KboRosterParser.parseConfirmed(KboRosterResponse(empty, empty, "500"))
        }
    }
    @Test fun missingTableIsNotAnEmptyPublication() {
        assertThrows(Exception::class.java) {
            KboRosterParser.parseConfirmed(KboRosterResponse("", empty, "100"))
        }
        assertThrows(Exception::class.java) {
            KboRosterParser.parseConfirmed(KboRosterResponse("{}", empty, "100"))
        }
    }
    @Test fun actualRowShapePreservesRegisteredPlayer() {
        val table = """{"rows":[{"row":[{"Text":"24"},{"Text":"김태혁"},{"Text":"투수"},{"Text":"우투우타"}]}]}"""
        assertEquals("김태혁", KboRosterParser.parseConfirmed(KboRosterResponse(table, empty, "100")).first.single().name)
    }
}
