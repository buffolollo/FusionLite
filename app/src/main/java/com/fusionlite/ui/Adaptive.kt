package com.fusionlite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Mobile-first page frame shared by every tab.
 *
 * - centres the column and caps it at [FusionDesignSystem.maxContentWidth] on
 *   tablets/landscape
 * - widens the gutter at 600dp and 840dp so text never runs edge to edge
 * - reserves bottom padding so content clears the floating navigation bar
 * - keeps a single 4/8dp vertical rhythm between cards
 */
@Composable
fun FusionPage(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = LocalFusionDesign.current.navClearance,
    topPadding: Dp = 8.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(LocalFusionDesign.current.pageGap),
    content: LazyListScope.() -> Unit,
) {
    val design = LocalFusionDesign.current
    BoxWithConstraints(
        modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val gutter = design.gutterFor(maxWidth)
        // The bottom bar is already inset by navigationBarsPadding(); content
        // must clear both the bar and the system gesture/button area.
        val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            Modifier
                .fillMaxSize()
                .widthIn(max = design.maxContentWidth),
            contentPadding = PaddingValues(
                start = gutter,
                end = gutter,
                top = topPadding,
                bottom = bottomPadding + navInset,
            ),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/** Same frame for a non-lazy screen (login, full-screen chart). */
@Composable
fun FusionStaticPage(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val design = LocalFusionDesign.current
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val gutter = design.gutterFor(maxWidth)
        Box(
            Modifier
                .fillMaxSize()
                .widthIn(max = design.maxContentWidth)
                .padding(horizontal = gutter),
        ) {
            content()
        }
    }
}

/**
 * Adaptive grid: one column on narrow phones, up to [maxColumns] on tablets.
 * Items share the row evenly so KPI tiles never stretch into unreadable
 * ribbons or collapse below [minItemWidth].
 */
@Composable
fun FusionGrid(
    itemCount: Int,
    modifier: Modifier = Modifier,
    minItemWidth: Dp = 156.dp,
    spacing: Dp = 12.dp,
    maxColumns: Int = 4,
    item: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    if (itemCount <= 0) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = (((maxWidth + spacing) / (minItemWidth + spacing)).toInt())
            .coerceIn(1, maxColumns)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            for (rowStart in 0 until itemCount step columns) {
                val rowSize = minOf(columns, itemCount - rowStart)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    for (offset in 0 until rowSize) {
                        Box(Modifier.weight(1f)) {
                            item(rowStart + offset, Modifier.fillMaxWidth())
                        }
                    }
                    repeat(columns - rowSize) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
