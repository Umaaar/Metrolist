/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.compose.runtime.Immutable
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.db.entities.Song
import java.text.Collator
import java.util.Locale

enum class DownloadGroupType { PLAYLIST, ALBUM, ARTIST }

@Immutable
data class DownloadGroup(
    val key: String,
    val type: DownloadGroupType,
    /** Null only for the artist group of songs without any artist. */
    val title: String?,
    val thumbnailUrl: String?,
    val songs: List<Song>,
)

/**
 * Groups downloaded songs into library playlists, then albums, then (for songs in neither)
 * their primary artist. A song may be in several groups; every song is in at least one.
 * Songs keep the order of [songs] inside each group.
 */
internal fun groupDownloadedSongs(
    songs: List<Song>,
    playlists: List<PlaylistEntity>,
    playlistSongMaps: List<PlaylistSongMap>,
): List<DownloadGroup> {
    val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
    val songIdsByPlaylist = playlistSongMaps.groupBy({ it.playlistId }, { it.songId }).mapValues { it.value.toHashSet() }

    val playlistGroups =
        playlists.mapNotNull { playlist ->
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
    val inPlaylist = playlistGroups.flatMapTo(HashSet()) { group -> group.songs.map { it.id } }

    val albumGroups =
        songs
            .groupBy { it.song.albumId ?: it.album?.id }
            .mapNotNull { (albumId, groupSongs) ->
                albumId ?: return@mapNotNull null
                val first = groupSongs.first()
                DownloadGroup(
                    key = "album:$albumId",
                    type = DownloadGroupType.ALBUM,
                    title = first.album?.title ?: first.song.albumName,
                    thumbnailUrl = first.album?.thumbnailUrl ?: first.thumbnailUrl,
                    songs = groupSongs,
                )
            }.sortedWith(compareBy(collator) { it.title.orEmpty() })

    val artistGroups =
        songs
            .filter { (it.song.albumId ?: it.album?.id) == null && it.id !in inPlaylist }
            .groupBy { it.orderedArtists.firstOrNull()?.id }
            .map { (artistId, groupSongs) ->
                val artist = groupSongs.first().orderedArtists.firstOrNull()
                DownloadGroup(
                    key = "artist:${artistId.orEmpty()}",
                    type = DownloadGroupType.ARTIST,
                    title = artist?.name,
                    thumbnailUrl = artist?.thumbnailUrl ?: groupSongs.first().thumbnailUrl,
                    songs = groupSongs,
                )
            }.sortedWith(compareBy<DownloadGroup> { it.title == null }.thenBy(collator) { it.title.orEmpty() })

    return playlistGroups + albumGroups + artistGroups
}
