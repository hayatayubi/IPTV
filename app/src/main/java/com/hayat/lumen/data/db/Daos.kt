package com.hayat.lumen.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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
        SELECT c.id, c.name, c.number, c.logoUrl, c.playlistOrder
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
