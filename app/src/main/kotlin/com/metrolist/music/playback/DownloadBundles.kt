/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

/** One thing the user downloaded as a whole: an online playlist or mix, an album, a library playlist. */
@Serializable
data class DownloadBundle(
    val id: String,
    val kind: Kind,
    val title: String,
    val thumbnailUrl: String? = null,
    val songIds: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
) {
    @Serializable
    enum class Kind { PLAYLIST, ALBUM, LOCAL_PLAYLIST, AUTO_PLAYLIST }
}

/**
 * Returns this list with the bundle recorded: new bundles are added, and an existing one gains the
 * song ids it did not have yet. Returns the same instance when nothing changed.
 */
internal fun List<DownloadBundle>.withRecorded(
    id: String,
    kind: DownloadBundle.Kind,
    title: String,
    thumbnailUrl: String?,
    songIds: List<String>,
    now: Long,
): List<DownloadBundle> {
    val existing = find { it.id == id }
    if (existing == null) {
        if (songIds.isEmpty()) return this
        return this + DownloadBundle(id, kind, title, thumbnailUrl, songIds.distinct(), now, now)
    }
    val known = existing.songIds.toHashSet()
    val added = songIds.filter { known.add(it) }
    val newThumbnail = thumbnailUrl ?: existing.thumbnailUrl
    if (added.isEmpty() && title == existing.title && newThumbnail == existing.thumbnailUrl) return this
    val updated =
        existing.copy(
            title = title,
            thumbnailUrl = newThumbnail,
            songIds = existing.songIds + added,
            updatedAt = if (added.isEmpty()) existing.updatedAt else now,
        )
    return map { if (it.id == id) updated else it }
}

class DownloadBundleStore(
    private val file: File,
) {
    private val _bundles = MutableStateFlow(read(file))
    val bundles = _bundles.asStateFlow()

    @Synchronized
    fun record(
        id: String,
        kind: DownloadBundle.Kind,
        title: String,
        thumbnailUrl: String?,
        songIds: List<String>,
        now: Long = System.currentTimeMillis(),
    ) {
        val current = _bundles.value
        val updated = current.withRecorded(id, kind, title, thumbnailUrl, songIds, now)
        if (updated === current) return
        _bundles.value = updated
        runCatching {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(updated))
            if (!temp.renameTo(file)) error("rename failed")
        }.onFailure { Timber.tag(TAG).e(it, "Failed to save download bundles") }
    }

    companion object {
        const val FILE_NAME = "download_bundles.json"
        private const val TAG = "DownloadBundleStore"
        private val json = Json { ignoreUnknownKeys = true }

        fun read(file: File): List<DownloadBundle> =
            runCatching { if (file.exists()) json.decodeFromString<List<DownloadBundle>>(file.readText()) else emptyList() }
                .onFailure { Timber.tag(TAG).e(it, "Failed to read download bundles") }
                .getOrDefault(emptyList())
    }
}
