package com.bossxor.lottegiants

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherIconTest {

    @Test
    fun normalizeKnownCodes() {
        assertEquals("LT", LauncherIcon.normalize("lt"))
        assertEquals("OB", LauncherIcon.normalize(" OB "))
        assertEquals("WO", LauncherIcon.normalize("wo"))
    }

    @Test
    fun normalizeUnknownFallsBackToLotte() {
        assertEquals("LT", LauncherIcon.normalize(""))
        assertEquals("LT", LauncherIcon.normalize("XX"))
    }

    @Test
    fun aliasClassNameUsesPackageAndCode() {
        assertEquals(
            "${BuildConfig.APPLICATION_ID}.Launcher_NC",
            LauncherIcon.aliasClassName("nc"),
        )
    }
}
