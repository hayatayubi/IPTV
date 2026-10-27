package com.hayat.lumen.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SourceType { M3U, XTREAM }

/**
 * A playlist the user added. Credentials live only here, on-device.
 *
 * [generation] is bumped on every successful refresh. Channels and groups are
 * written under the new generation, then the old one is deleted in one short
 * transaction, so the UI never sees a half-imported playlist.
 */
@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: SourceType,
    val name: String,
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val epgUrl: String? = null,
    val lastRefreshed: Long? = null,
    val generation: Int = 0,
)

/** Playlist data only. User state (favourites, later order/hidden) lives in its own tables, keyed on stableKey. */
@Entity(
    tableName = "channel_groups",
    indices = [Index(value = ["sourceId", "generation", "playlistOrder"])],
)
data class ChannelGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val generation: Int,
    val name: String,
    val playlistOrder: Int,
    val channelCount: Int,
)

@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["sourceId", "generation", "groupName", "playlistOrder"]),
        Index(value = ["sourceId", "stableKey"]),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val generation: Int,
    val stableKey: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String?,
    val groupName: String,
    val tvgId: String?,
    val number: Int?,
    val playlistOrder: Int,
)

/**
 * A favourite channel. Keyed on (sourceId, stableKey), not the channel row id, because
 * row ids change on every refresh while stable keys survive it.
 * [position] orders the Home row: new favourites go to the end.
 */
@Entity(tableName = "favourites", primaryKeys = ["sourceId", "stableKey"])
data class FavouriteEntity(
    val sourceId: Long,
    val stableKey: String,
    val position: Int,
    val addedAt: Long,
)

/** Projection for the Live groups column. */
data class GroupRow(
    val sourceId: Long,
    val sourceName: String,
    val name: String,
    val channelCount: Int,
)

/** Projection for channel lists: no stream URL, so 10k rows stay small. */
data class ChannelRow(
    val id: Long,
    val name: String,
    val number: Int?,
    val logoUrl: String?,
    val playlistOrder: Int,
    val favourite: Boolean,
)

/** Projection for the Home favourites row. */
data class FavouriteRow(
    val id: Long,
    val sourceId: Long,
    val stableKey: String,
    val name: String,
    val number: Int?,
    val logoUrl: String?,
)

/** What the player needs to zap through a group. */
data class PlayableChannel(
    val id: Long,
    val name: String,
    val number: Int?,
    val streamUrl: String,
    val groupName: String,
    val playlistOrder: Int,
    val logoUrl: String?,
)

/**
 * One TV guide entry, from a source's XMLTV feed. [channelKey] is the XMLTV channel id,
 * lowercased, and matches `lower(channels.tvgId)` (Xtream's `epg_channel_id`).
 * Only programmes for channels in the playlist, and only the next day or so, are kept.
 */
@Entity(
    tableName = "programmes",
    indices = [Index(value = ["sourceId", "batch", "channelKey", "stop"])],
)
data class ProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val batch: Long,
    val channelKey: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
)

/**
 * Guide status per source. Programmes are written under a new [batch], then [batch] is
 * switched in one transaction (like channel generations), so a failed update keeps the old guide.
 */
@Entity(tableName = "epg_state")
data class EpgStateEntity(
    @PrimaryKey val sourceId: Long,
    val batch: Long,
    val updatedAt: Long?,
    val programmeCount: Int,
    val channelCount: Int,
    val error: String?,
)

/** A programme joined to the playlist channel it belongs to. */
data class ChannelProgramme(
    val channelId: Long,
    val title: String,
    val description: String?,
    val start: Long,
    val stop: Long,
)
