package com.hayat.lumen.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Room generates the implementations at build time (via KSP).
// Returning Flow makes a query live: it re-emits whenever the tables change.

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY id")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT COUNT(*) FROM sources")
    suspend fun count(): Int

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun get(id: Long): SourceEntity?

    @Insert
    suspend fun insert(source: SourceEntity): Long

    @Query("UPDATE sources SET generation = :generation, lastRefreshed = :at WHERE id = :id")
    suspend fun markRefreshed(id: Long, generation: Int, at: Long)

    @Query("UPDATE sources SET epgUrl = :epgUrl WHERE id = :id AND epgUrl IS NULL")
    suspend fun setEpgUrlIfMissing(id: Long, epgUrl: String)

    /** A user-entered guide URL; null means "automatic" (the playlist's url-tvg, or Xtream's xmltv.php). */
    @Query("UPDATE sources SET epgUrl = :epgUrl WHERE id = :id")
    suspend fun setEpgUrl(id: Long, epgUrl: String?)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ChannelDao {
    @Insert
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Insert
    suspend fun insertGroups(groups: List<ChannelGroupEntity>)

    /** Live rows only ever come from a source's current generation. */
    @Query(
        """
        SELECT g.sourceId, s.name AS sourceName, g.name, g.channelCount
        FROM channel_groups g JOIN sources s ON s.id = g.sourceId AND s.generation = g.generation
        ORDER BY g.sourceId, g.playlistOrder
        """,
    )
    fun observeGroups(): Flow<List<GroupRow>>

    @Query(
        """
        SELECT c.id, c.name, c.number, c.logoUrl, c.playlistOrder,
            EXISTS(SELECT 1 FROM favourites f WHERE f.sourceId = c.sourceId AND f.stableKey = c.stableKey) AS favourite
        FROM channels c JOIN sources s ON s.id = c.sourceId AND s.generation = c.generation
        WHERE c.sourceId = :sourceId AND c.groupName = :groupName
        ORDER BY c.playlistOrder
        """,
    )
    fun observeChannels(sourceId: Long, groupName: String): Flow<List<ChannelRow>>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun get(id: Long): ChannelEntity?

    @Query(
        """
        SELECT c.id, c.name, c.number, c.streamUrl, c.groupName, c.playlistOrder
        FROM channels c JOIN sources s ON s.id = c.sourceId AND s.generation = c.generation
        WHERE c.sourceId = :sourceId AND c.groupName = :groupName
        ORDER BY c.playlistOrder
        """,
    )
    suspend fun playableInGroup(sourceId: Long, groupName: String): List<PlayableChannel>

    @Query("DELETE FROM channels WHERE sourceId = :sourceId AND generation != :keep")
    suspend fun deleteChannelsExcept(sourceId: Long, keep: Int)

    @Query("DELETE FROM channel_groups WHERE sourceId = :sourceId AND generation != :keep")
    suspend fun deleteGroupsExcept(sourceId: Long, keep: Int)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId AND generation = :generation")
    suspend fun deleteChannelsOf(sourceId: Long, generation: Int)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    suspend fun deleteAllChannels(sourceId: Long)

    @Query("DELETE FROM channel_groups WHERE sourceId = :sourceId")
    suspend fun deleteAllGroups(sourceId: Long)
}

@Dao
interface EpgDao {
    @Insert
    suspend fun insertProgrammes(programmes: List<ProgrammeEntity>)

    @Query("SELECT * FROM epg_state WHERE sourceId = :sourceId")
    suspend fun state(sourceId: Long): EpgStateEntity?

    @Query("SELECT * FROM epg_state")
    fun observeStates(): Flow<List<EpgStateEntity>>

    @Upsert
    suspend fun upsertState(state: EpgStateEntity)

    @Query("UPDATE epg_state SET error = :error WHERE sourceId = :sourceId")
    suspend fun setError(sourceId: Long, error: String?)

    @Query("DELETE FROM programmes WHERE sourceId = :sourceId AND batch != :keep")
    suspend fun deleteProgrammesExcept(sourceId: Long, keep: Long)

    @Query("DELETE FROM programmes WHERE sourceId = :sourceId AND batch = :batch")
    suspend fun deleteBatch(sourceId: Long, batch: Long)

    @Query("DELETE FROM programmes WHERE sourceId = :sourceId")
    suspend fun deleteAllProgrammes(sourceId: Long)

    @Query("DELETE FROM epg_state WHERE sourceId = :sourceId")
    suspend fun deleteState(sourceId: Long)

    /** Lowercased guide ids of a source's current channels: the only ones worth storing programmes for. */
    @Query(
        """
        SELECT DISTINCT lower(c.tvgId) FROM channels c JOIN sources s ON s.id = c.sourceId AND s.generation = c.generation
        WHERE c.sourceId = :sourceId AND c.tvgId IS NOT NULL AND c.tvgId != ''
        """,
    )
    suspend fun guideKeys(sourceId: Long): List<String>

    // The two queries below return programmes overlapping [now, until), oldest first per channel.
    // A Flow re-runs them whenever programmes or epg_state change.

    @Query(
        """
        SELECT c.id AS channelId, p.title, p.description, p.start, p.stop
        FROM channels c
        JOIN sources s ON s.id = c.sourceId AND s.generation = c.generation
        JOIN epg_state e ON e.sourceId = c.sourceId
        JOIN programmes p ON p.sourceId = c.sourceId AND p.batch = e.batch AND p.channelKey = lower(c.tvgId)
        WHERE c.sourceId = :sourceId AND c.groupName = :groupName AND p.stop > :now AND p.start < :until
        ORDER BY c.id, p.start
        """,
    )
    fun observeForGroup(sourceId: Long, groupName: String, now: Long, until: Long): Flow<List<ChannelProgramme>>

    @Query(
        """
        SELECT c.id AS channelId, p.title, p.description, p.start, p.stop
        FROM channels c
        JOIN epg_state e ON e.sourceId = c.sourceId
        JOIN programmes p ON p.sourceId = c.sourceId AND p.batch = e.batch AND p.channelKey = lower(c.tvgId)
        WHERE c.id IN (:channelIds) AND p.stop > :now AND p.start < :until
        ORDER BY c.id, p.start
        """,
    )
    fun observeForChannels(channelIds: List<Long>, now: Long, until: Long): Flow<List<ChannelProgramme>>
}

/**
 * Favourites join to the channel rows of the source's current generation, so a favourite
 * whose channel vanished from the playlist just doesn't show (and comes back if it returns).
 */
@Dao
interface FavouriteDao {
    @Query(
        """
        SELECT c.id, c.sourceId, c.stableKey, c.name, c.number, c.logoUrl
        FROM favourites f
        JOIN sources s ON s.id = f.sourceId
        JOIN channels c ON c.sourceId = f.sourceId AND c.stableKey = f.stableKey AND c.generation = s.generation
        ORDER BY f.position
        """,
    )
    fun observe(): Flow<List<FavouriteRow>>

    @Query(
        """
        SELECT c.id, c.name, c.number, c.streamUrl, c.groupName, c.playlistOrder
        FROM favourites f
        JOIN sources s ON s.id = f.sourceId
        JOIN channels c ON c.sourceId = f.sourceId AND c.stableKey = f.stableKey AND c.generation = s.generation
        ORDER BY f.position
        """,
    )
    suspend fun playable(): List<PlayableChannel>

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE sourceId = :sourceId AND stableKey = :stableKey)")
    suspend fun contains(sourceId: Long, stableKey: String): Boolean

    @Query(
        """
        INSERT OR IGNORE INTO favourites (sourceId, stableKey, position, addedAt)
        VALUES (:sourceId, :stableKey, (SELECT COALESCE(MAX(position), -1) + 1 FROM favourites), :at)
        """,
    )
    suspend fun add(sourceId: Long, stableKey: String, at: Long)

    @Query("DELETE FROM favourites WHERE sourceId = :sourceId AND stableKey = :stableKey")
    suspend fun remove(sourceId: Long, stableKey: String)

    @Query("DELETE FROM favourites WHERE sourceId = :sourceId")
    suspend fun removeAllOf(sourceId: Long)
}
