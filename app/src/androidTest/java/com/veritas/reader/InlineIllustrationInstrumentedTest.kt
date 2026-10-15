package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import com.veritas.reader.ui.screens.ReaderInlineIllustration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InlineIllustrationInstrumentedTest {
    @get:Rule val rule = createComposeRule()
    @Test fun delayedBitmapDoesNotMoveThePageOrScrollPosition() {
        val decoded = mutableStateOf<Bitmap?>(null)
        lateinit var pager: PagerState
        lateinit var scroll: ScrollState
        lateinit var scope: CoroutineScope
        rule.setContent {
            MaterialTheme {
                pager = rememberPagerState(initialPage = 1) { 3 }
                scope = rememberCoroutineScope()
                HorizontalPager(pager, modifier = Modifier.fillMaxSize()) { page ->
                    val pageScroll = rememberScrollState()
                    if (page == 1) scroll = pageScroll
                    Column(Modifier.fillMaxSize().verticalScroll(pageScroll)) {
                        Text("Before the illustration on page $page")
                        Spacer(Modifier.height(220.dp))
                        ReaderInlineIllustration(decoded.value, 0)
                        Text("After the illustration on page $page")
                        Spacer(Modifier.height(600.dp))
                    }
                }
            }
        }
        rule.runOnIdle { scope.launch { scroll.scrollTo(150) } }
        rule.waitForIdle()
        var before = 0
        rule.runOnIdle {
            before = scroll.value
            assertEquals(1, pager.settledPage)
            decoded.value = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(1, pager.settledPage)
            assertEquals(before, scroll.value)
        }
        rule.onNodeWithContentDescription("Illustration 1").assertIsDisplayed()
    }
}
