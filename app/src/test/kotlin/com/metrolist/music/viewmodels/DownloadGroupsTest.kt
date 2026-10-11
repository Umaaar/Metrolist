package com.metrolist.music.viewmodels

import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.playback.DownloadBundle
import com.metrolist.music.playback.withRecorded
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadGroupsTest {
    private fun song(id: String) =
        Song(
            song = SongEntity(id = id, title = "title-$id", isDownloaded = true),
            artists = emptyList(),
        )

    private fun bundle(
        id: String,
        songIds: List<String>,
        updatedAt: Long,
    ) = DownloadBundle(
        id = id,
        kind = DownloadBundle.Kind.PLAYLIST,
        title = "bundle-$id",
        songIds = songIds,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private val playlist = PlaylistEntity(id = "p1", name = "Mine", browseId = "VLp1")

    @Test
    fun `bundles newest first, then library playlists, then individual downloads`() {
        val songs = listOf("a", "b", "c", "d", "e").map(::song)
        val bundles =
            listOf(
                bundle("old", listOf("b", "a", "gone"), updatedAt = 1),
                bundle("new", listOf("c"), updatedAt = 2),
                bundle("empty", listOf("gone"), updatedAt = 3),
            )
        val maps = listOf(PlaylistSongMap(playlistId = "p1", songId = "d"))

        val groups = groupDownloadedSongs(songs, bundles, listOf(playlist), maps)

        assertEquals(listOf("bundle:new", "bundle:old", "playlist:p1", "individual"), groups.map { it.key })
        assertEquals(listOf("b", "a"), groups[1].songs.map { it.id })
        assertEquals(listOf("e"), groups[3].songs.map { it.id })
    }

    @Test
    fun `library playlist already recorded as a bundle is not shown twice`() {
        val groups =
            groupDownloadedSongs(
                songs = listOf(song("a")),
                bundles = listOf(bundle("VLp1", listOf("a"), updatedAt = 1)),
                playlists = listOf(playlist),
                playlistSongMaps = listOf(PlaylistSongMap(playlistId = "p1", songId = "a")),
            )

        assertEquals(listOf("bundle:VLp1"), groups.map { it.key })
    }

    @Test
    fun `every downloaded song lands in at least one group`() {
        val songs = (0 until 50).map { song("s$it") }
        val bundles = listOf(bundle("x", songs.filterIndexed { i, _ -> i % 3 == 0 }.map { it.id }, updatedAt = 1))
        val maps = songs.filterIndexed { i, _ -> i % 4 == 1 }.map { PlaylistSongMap(playlistId = "p1", songId = it.id) }

        val grouped = groupDownloadedSongs(songs, bundles, listOf(playlist), maps).flatMap { it.songs.map(Song::id) }.toSet()

        assertTrue(songs.all { it.id in grouped })
    }

    @Test
    fun `recording a bundle again merges new songs and keeps known ones`() {
        val first = emptyList<DownloadBundle>().withRecorded("x", DownloadBundle.Kind.PLAYLIST, "Mix", null, listOf("a", "b"), now = 1)
        val unchanged = first.withRecorded("x", DownloadBundle.Kind.PLAYLIST, "Mix", null, listOf("b", "a"), now = 2)
        val merged = first.withRecorded("x", DownloadBundle.Kind.PLAYLIST, "Mix", "thumb", listOf("c", "a"), now = 3)

        assertSame(first, unchanged)
        assertEquals(listOf("a", "b", "c"), merged.single().songIds)
        assertEquals("thumb", merged.single().thumbnailUrl)
        assertEquals(1L, merged.single().createdAt)
        assertEquals(3L, merged.single().updatedAt)
    }
}
