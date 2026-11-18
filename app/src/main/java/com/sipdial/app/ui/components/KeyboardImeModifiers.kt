package com.sipdial.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Automatically scrolls the enclosing scrollable container so a focused input field
 * (plus comfortable vertical breathing room) remains unobstructed above the IME/keyboard.
 * Re-triggers smoothly while IME insets animate open, without fighting manual user
 * scrolling once keyboard height settles or when the keyboard dismisses.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bringIntoViewOnFocusWithIme(
    extraVerticalMargin: Dp = 20.dp
): Modifier = composed {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val extraMarginPx = with(density) { extraVerticalMargin.toPx() }
    val imeInsets = WindowInsets.ime

    var isFocused by remember { mutableStateOf(false) }
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    var lastObservedImeBottom by remember { mutableIntStateOf(0) }

    suspend fun requestFieldVisible() {
        withFrameNanos { }
        val width = fieldSize.width.toFloat().coerceAtLeast(1f)
        val height = fieldSize.height.toFloat().coerceAtLeast(1f)
        bringIntoViewRequester.bringIntoView(
            Rect(
                left = 0f,
                top = -extraMarginPx,
                right = width,
                bottom = height + extraMarginPx
            )
        )
    }

    LaunchedEffect(isFocused) {
        if (!isFocused) {
            lastObservedImeBottom = imeInsets.getBottom(density)
            return@LaunchedEffect
        }

        requestFieldVisible()

        lastObservedImeBottom = imeInsets.getBottom(density)
        snapshotFlow { imeInsets.getBottom(density) }
            .distinctUntilChanged()
            .collectLatest { imeBottom ->
                val grew = imeBottom > lastObservedImeBottom
                lastObservedImeBottom = imeBottom
                if (grew && imeBottom > 0) {
                    delay(60L)
                    requestFieldVisible()
                }
            }
    }

    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onSizeChanged { fieldSize = it }
        .onFocusChanged { focusState -> isFocused = focusState.isFocused }
}
