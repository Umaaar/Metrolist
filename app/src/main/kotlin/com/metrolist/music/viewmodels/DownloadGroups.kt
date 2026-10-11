/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.compose.runtime.Immutable
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.db.entities.Song
import com.metrolist.music.playback.DownloadBundle
import java.text.Collator
import java.util.Locale

enum class DownloadGroupType { BUNDLE, PLAYLIST, INDIVIDUAL }

@Immutable
data class DownloadGroup(
    val key: String,
    val type: DownloadGroupType,
    /** Null for the individual downloads group. */
    val title: String?,
    val thumbnailUrl: String?,
    val songs: List<Song>,
)

/**
 * Groups downloaded songs into recorded download bundles (most recent first), then library
 * playlists that are not already a bundle, then one group of everything left over.
 * A song may be in several groups; every song is in at least one.
 */
internal fun groupDownloadedSongs(
    songs: List<Song>,
    bundles: List<DownloadBundle>,
    playlists: List<PlaylistEntity>,
    playlistSongMaps: List<PlaylistSongMap>,
): List<DownloadGroup> {
    val songsById = songs.associateBy { it.id }

    val bundleGroups =
        bundles.sortedByDescending { it.updatedAt }.mapNotNull { bundle ->
            val groupSongs = bundle.songIds.mapNotNull(songsById::get)
            if (groupSongs.isEmpty()) return@mapNotNull null
            DownloadGroup(
                key = "bundle:${bundle.id}",
                type = DownloadGroupType.BUNDLE,
                title = bundle.title,
                thumbnailUrl = bundle.thumbnailUrl ?: groupSongs.first().thumbnailUrl,
                songs = groupSongs,
            )
        }

    val bundleIds = bundles.mapTo(HashSet()) { it.id }
    val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
    val songIdsByPlaylist = playlistSongMaps.groupBy({ it.playlistId }, { it.songId }).mapValues { it.value.toHashSet() }
    val playlistGroups =
        playlists
            .filter { it.id !in bundleIds && it.browseId !in bundleIds }
            .mapNotNull { playlist ->
                val ids = songIdsByPlaylist[playlist.id] ?: return@mapNotNull null
                val groupSongs = songs.filter { it.id in ids }
                if (groupSongs.isEmpty()) return@mapNotNull null
                DownloadGroup(
                    key = "playlist:${playlist.id}",
                    type = DownloadGroupType.PLAYLIST,
                    title = playlist.name,
                    thumbnailUrl = playlist.thumbnailUrl ?: groupSongs.first().thumbnailUrl,
                    songs = groupSongs,
                )
            }.sortedWith(compareBy(collator) { it.title.orEmpty() })

    val grouped = (bundleGroups + playlistGroups).flatMapTo(HashSet()) { group -> group.songs.map { it.id } }
    val individualGroup =
        songs.filter { it.id !in grouped }.takeIf { it.isNotEmpty() }?.let { individual ->
            DownloadGroup(
                key = "individual",
                type = DownloadGroupType.INDIVIDUAL,
                title = null,
                thumbnailUrl = individual.first().thumbnailUrl,
                songs = individual,
            )
        }

    return bundleGroups + playlistGroups + listOfNotNull(individualGroup)
}
