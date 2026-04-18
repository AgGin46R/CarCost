package com.aggin.carcost.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer

/**
 * Base skeleton block with shimmer effect.
 * Use to mock a single piece of UI (text line, avatar, thumbnail, etc).
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    width: Dp? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
) {
    val base = modifier
        .shimmer()
        .height(height)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    Spacer(
        modifier = if (width != null) base.width(width) else base.fillMaxWidth()
    )
}

/** Circle placeholder (avatars, icons). */
@Composable
fun SkeletonCircle(size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .shimmer()
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

/** Card-shaped placeholder with two text lines inside (Home / Analytics cards). */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonBlock(height = 20.dp, width = 160.dp)
            SkeletonBlock(height = (height.value * 0.55f).dp)
            SkeletonBlock(height = 14.dp, width = 100.dp)
        }
    }
}

/** Chat list row — avatar + two text lines + unread chip. */
@Composable
fun SkeletonListRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SkeletonCircle(size = 48.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkeletonBlock(height = 16.dp, width = 140.dp)
            SkeletonBlock(height = 14.dp, width = 220.dp)
        }
        SkeletonBlock(height = 20.dp, width = 28.dp, shape = CircleShape)
    }
}

/** Vertical list of skeleton cards — for Home screen loading. */
@Composable
fun SkeletonCardList(
    count: Int = 3,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    cardHeight: Dp = 140.dp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(count) { SkeletonCard(height = cardHeight) }
    }
}

/** Chat list skeleton — column of rows. */
@Composable
fun SkeletonChatList(count: Int = 6) {
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(count) { SkeletonListRow() }
    }
}
