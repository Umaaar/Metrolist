package com.metrolist.music.viewmodels

import com.metrolist.music.db.entities.AlbumEntity
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadGroupsTest {
    private fun song(
        id: String,
        albumId: String? = null,
        albumEntity: AlbumEntity? = null,
        artist: String? = null,
    ) = Song(
        song = SongEntity(id = id, title = "title-$id", albumId = albumId, albumName = albumId?.let { "name-$it" }, isDownloaded = true),
        artists = listOfNotNull(artist?.let { ArtistEntity(id = it, name = "artist-$it") }),
        album = albumEntity,
    )

    private val playlist = PlaylistEntity(id = "p1", name = "Mix")

    @Test
    fun `groups by playlist then album then artist`() {
        val songs =
            listOf(
                song("a", albumId = "al1", artist = "x"),
                song("b", artist = "x"),
                song("c", artist = "y"),
                song("d"),
                song("e", albumEntity = AlbumEntity(id = "al2", title = "Album 2", songCount = 1, duration = 0)),
            )
        val maps = listOf(PlaylistSongMap(playlistId = "p1", songId = "a"), PlaylistSongMap(playlistId = "p1", songId = "c"))

        val groups = groupDownloadedSongs(songs, listOf(playlist), maps)

        assertEquals(
            listOf("playlist:p1", "album:al2", "album:al1", "artist:x", "artist:"),
            groups.map { it.key },
        )
        assertEquals(listOf("a", "c"), groups[0].songs.map { it.id })
        assertEquals("Album 2", groups[1].title)
        assertEquals("name-al1", groups[2].title)
        assertEquals(listOf("b"), groups[3].songs.map { it.id })
        assertEquals(null, groups[4].title)
    }

    @Test
    fun `every downloaded song lands in at least one group`() {
        val songs = (0 until 50).map { i ->
            song(
                id = "s$i",
                albumId = if (i % 3 == 0) "al${i % 4}" else null,
                artist = if (i % 5 == 0) null else "ar${i % 7}",
            )
        }
        val maps = songs.filterIndexed { i, _ -> i % 4 == 1 }.map { PlaylistSongMap(playlistId = "p1", songId = it.id) }

        val grouped = groupDownloadedSongs(songs, listOf(playlist), maps).flatMap { it.songs.map(Song::id) }.toSet()

        assertTrue(songs.all { it.id in grouped })
    }

    @Test
    fun `playlists without downloaded songs are dropped`() {
        val groups =
            groupDownloadedSongs(
                songs = listOf(song("a", artist = "x")),
                playlists = listOf(playlist),
                playlistSongMaps = listOf(PlaylistSongMap(playlistId = "p1", songId = "gone")),
            )

        assertEquals(listOf("artist:x"), groups.map { it.key })
    }
}
