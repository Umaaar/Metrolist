/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.GridThumbnailHeight
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.ui.component.EmptyPlaceholder
import com.metrolist.music.ui.component.GridItem
import com.metrolist.music.ui.component.ItemThumbnail
import com.metrolist.music.viewmodels.DownloadGroup
import com.metrolist.music.viewmodels.DownloadGroupType

@Composable
fun DownloadGroup.displayTitle(): String = title ?: stringResource(R.string.unknown)

@Composable
fun DownloadGroupsGrid(
    groups: List<DownloadGroup>?,
    onGroupClick: (DownloadGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = modifier,
    ) {
        if (groups?.isEmpty() == true) {
            item(key = "empty_placeholder", span = { GridItemSpan(maxLineSpan) }) {
                EmptyPlaceholder(
                    icon = R.drawable.music_note,
                    text = stringResource(R.string.playlist_is_empty),
                )
            }
        }

        items(items = groups.orEmpty(), key = { it.key }) { group ->
            GridItem(
                title = group.displayTitle(),
                subtitle = pluralStringResource(R.plurals.n_song, group.songs.size, group.songs.size),
                thumbnailContent = {
                    ItemThumbnail(
                        thumbnailUrl = group.thumbnailUrl,
                        isActive = false,
                        isPlaying = false,
                        shape =
                            if (group.type == DownloadGroupType.ARTIST) {
                                CircleShape
                            } else {
                                RoundedCornerShape(ThumbnailCornerRadius)
                            },
                    )
                },
                fillMaxWidth = true,
                modifier =
                    Modifier
                        .clickable { onGroupClick(group) }
                        .animateItem(),
            )
        }
    }
}
