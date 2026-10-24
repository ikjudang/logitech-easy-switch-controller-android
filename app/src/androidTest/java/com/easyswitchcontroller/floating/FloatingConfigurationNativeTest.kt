package com.easyswitchcontroller.floating

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android Configuration.diff/copy behavior; does not attach windows or read the screen. */
@RunWith(AndroidJUnit4::class)
class FloatingConfigurationNativeTest {
    private fun baseline() = Configuration(InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration).apply {
        keyboard = Configuration.KEYBOARD_NOKEYS
        keyboardHidden = Configuration.KEYBOARDHIDDEN_YES
        hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES
        navigation = Configuration.NAVIGATION_NONAV
        navigationHidden = Configuration.NAVIGATIONHIDDEN_YES
    }

    @Test fun definedHidAttachDetachAndRepeatedCallbacksDoNotRebuild() {
        val changes = listOf<Pair<Int, (Configuration) -> Unit>>(
            ActivityInfo.CONFIG_KEYBOARD to { it.keyboard = Configuration.KEYBOARD_QWERTY },
            ActivityInfo.CONFIG_KEYBOARD_HIDDEN to { it.keyboardHidden = Configuration.KEYBOARDHIDDEN_NO },
            ActivityInfo.CONFIG_KEYBOARD_HIDDEN to { it.hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO },
            ActivityInfo.CONFIG_NAVIGATION to { it.navigation = Configuration.NAVIGATION_DPAD },
            ActivityInfo.CONFIG_KEYBOARD_HIDDEN to { it.navigationHidden = Configuration.NAVIGATIONHIDDEN_NO },
        )
        for ((expected, change) in changes) {
            val original = baseline()
            val tracker = FloatingConfigurationChanges(original)
            val attached = Configuration(original).also(change)
            assertEquals("Actual Android input diff", expected, tracker.update(attached))
            assertFalse(FloatingConfigurationChanges.requiresRebuild(expected))
            assertEquals("A duplicate callback is not another attach", 0, tracker.update(attached))
            val detached = tracker.update(original)
            assertEquals("Detach uses defined constants, not UNDEFINED", expected, detached)
            assertFalse(FloatingConfigurationChanges.requiresRebuild(detached))
        }
    }

    @Test fun displayAndResourceChangesStillRebuildIncludingMixedInputChanges() {
        val changes = listOf<Pair<Int, (Configuration) -> Unit>>(
            ActivityInfo.CONFIG_ORIENTATION to { it.orientation = if (it.orientation == Configuration.ORIENTATION_PORTRAIT)
                Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT },
            ActivityInfo.CONFIG_SCREEN_SIZE to { it.screenWidthDp += 40 },
            ActivityInfo.CONFIG_SCREEN_SIZE to { it.screenHeightDp += 40 },
            ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE to { it.smallestScreenWidthDp += 40 },
            ActivityInfo.CONFIG_DENSITY to { it.densityDpi += 40 },
            ActivityInfo.CONFIG_FONT_SCALE to { it.fontScale += .1f },
            ActivityInfo.CONFIG_LOCALE to { it.setLocales(LocaleList.forLanguageTags(
                if (it.locales.toLanguageTags() == "en-US") "ko-KR" else "en-US")) },
            ActivityInfo.CONFIG_UI_MODE to { it.uiMode = (it.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (it.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
                    Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES },
        )
        for ((expected, change) in changes) for (withHid in listOf(false, true)) {
            val original = baseline()
            val updated = Configuration(original).also(change)
            if (withHid) updated.keyboard = Configuration.KEYBOARD_QWERTY
            val actual = FloatingConfigurationChanges(original).update(updated)
            assertTrue("Expected real Android bit $expected in $actual", actual and expected != 0)
            assertTrue("Non-input changes must preserve the rebuild path", FloatingConfigurationChanges.requiresRebuild(actual))
        }
    }

    @Test fun callerMutationCannotChangeTheSavedConfiguration() {
        val original = baseline()
        val untouched = Configuration(original)
        val tracker = FloatingConfigurationChanges(original)
        original.densityDpi += 40
        assertEquals("The constructor must copy the Resources-owned object", 0, tracker.update(untouched))
        val attached = Configuration(untouched).apply { keyboard = Configuration.KEYBOARD_QWERTY }
        assertEquals(ActivityInfo.CONFIG_KEYBOARD, tracker.update(attached))
        val accepted = Configuration(attached)
        attached.screenWidthDp += 40
        assertEquals("Ignored callbacks must also save a defensive copy", 0, tracker.update(accepted))
    }

    @Test fun aNewOverlayCanRefreshItsBaselineWithoutChangingTheCallersObject() {
        val original = baseline()
        val tracker = FloatingConfigurationChanges(original)
        val refreshed = Configuration(original).apply { densityDpi += 40 }
        val expected = Configuration(refreshed)
        tracker.reset(refreshed)
        refreshed.screenHeightDp += 40
        assertEquals(0, tracker.update(expected))
        assertTrue(FloatingConfigurationChanges.requiresRebuild(tracker.update(original)))
    }
}
