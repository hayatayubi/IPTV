package com.hayat.lumen.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Films or series. In [WatchProgressEntity], SERIES means "an episode". */
enum class VodKind { MOVIE, SERIES }

/**
 * A movie or series category from the Xtream API. Written under the source's new
 * `vodGeneration` and swapped in at the end, like channel groups.
 */
@Entity(
    tableName = "vod_categories",
    indices = [Index(value = ["sourceId", "generation", "kind", "playlistOrder"])],
)
data class VodCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val generation: Int,
    val kind: VodKind,
    val categoryId: String,
    val name: String,
    val playlistOrder: Int,
    val itemCount: Int,
)

/** One film (`get_vod_streams`). Plot, cast and backdrop are fetched when it's focused or opened. */
@Entity(
    tableName = "movies",
    indices = [
        Index(value = ["sourceId", "generation", "categoryId", "playlistOrder"]),
        Index(value = ["sourceId", "streamId"]),
    ],
)
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val generation: Int,
    val streamId: String,
    val name: String,
    val posterUrl: String?,
    val rating: Double?,
    val categoryId: String,
    val extension: String,
    val added: Long?,
    val playlistOrder: Int,
)

/** One series (`get_series`). Its seasons and episodes are fetched when it's opened. */
@Entity(
    tableName = "series",
    indices = [
        Index(value = ["sourceId", "generation", "categoryId", "playlistOrder"]),
        Index(value = ["sourceId", "seriesId"]),
    ],
)
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val generation: Int,
    val seriesId: String,
    val name: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val plot: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: Double?,
    val categoryId: String,
    val playlistOrder: Int,
)

/**
 * How far into a film or episode you got. Keyed on the provider's ids, which survive refreshes.
 * Episodes also carry their series, season and number, so Continue watching can say "S1 · E3"
 * without fetching the series. [inContinue] = 0 once removed from Continue watching (until watched again).
 */
@Entity(
    tableName = "watch_progress",
    primaryKeys = ["sourceId", "kind", "itemId"],
    indices = [Index(value = ["sourceId", "seriesId"])],
)
data class WatchProgressEntity(
    val sourceId: Long,
    val kind: VodKind,
    val itemId: String,
    val seriesId: String?,
    val season: Int?,
    val episode: Int?,
    val positionMs: Long,
    val durationMs: Long,
    val finished: Boolean,
    @ColumnInfo(defaultValue = "1") val inContinue: Boolean = true,
    val updatedAt: Long,
)

/** A film or series added to My List. */
@Entity(tableName = "my_list", primaryKeys = ["sourceId", "kind", "itemId"])
data class MyListEntity(
    val sourceId: Long,
    val kind: VodKind,
    val itemId: String,
    val addedAt: Long,
)

/** A movie or series category hidden from the Movies/Series tabs. Category ids survive refreshes. */
@Entity(tableName = "hidden_vod_categories", primaryKeys = ["sourceId", "kind", "categoryId"])
data class HiddenVodCategoryEntity(
    val sourceId: Long,
    val kind: VodKind,
    val categoryId: String,
)

/** A row on the Movies/Series tab. [generation] keys the row's one-shot poster query. */
data class VodCategoryRow(
    val sourceId: Long,
    val generation: Int,
    val kind: VodKind,
    val categoryId: String,
    val name: String,
    val itemCount: Int,
)

/**
 * A poster: a film or a series. Continue watching fills the progress fields
 * ([season]/[episode] for a series' latest episode).
 */
data class Poster(
    val sourceId: Long,
    val kind: VodKind,
    val itemId: String,
    val name: String,
    val posterUrl: String?,
    val rating: Double?,
    val positionMs: Long? = null,
    val durationMs: Long? = null,
    val season: Int? = null,
    val episode: Int? = null,
) {
    val key get() = "$sourceId/$kind/$itemId"
    val progress: Float?
        get() = if (positionMs != null && durationMs != null && durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else null
}
