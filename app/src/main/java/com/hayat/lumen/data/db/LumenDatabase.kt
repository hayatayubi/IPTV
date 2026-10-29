package com.hayat.lumen.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SourceEntity::class, ChannelGroupEntity::class, ChannelEntity::class,
        ProgrammeEntity::class, EpgStateEntity::class, FavouriteEntity::class, HiddenGroupEntity::class,
        VodCategoryEntity::class, MovieEntity::class, SeriesEntity::class,
        WatchProgressEntity::class, MyListEntity::class, HiddenVodCategoryEntity::class,
        RenamedGroupEntity::class, RenamedVodCategoryEntity::class,
    ],
    version = 6,
    // Room compares the exported schema JSONs and writes the SQL itself.
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // adds the guide tables
        AutoMigration(from = 2, to = 3), // adds the favourites table
        AutoMigration(from = 3, to = 4), // adds hidden_groups and sources.hidden
        AutoMigration(from = 4, to = 5), // adds movies and series, watch progress, My List
        AutoMigration(from = 5, to = 6), // adds renamed categories (Live and VOD)
    ],
)
abstract class LumenDatabase : RoomDatabase() {
    abstract fun sources(): SourceDao
    abstract fun channels(): ChannelDao
    abstract fun epg(): EpgDao
    abstract fun favourites(): FavouriteDao
    abstract fun hidden(): HiddenDao
    abstract fun vod(): VodDao
    abstract fun vodUser(): VodUserDao
    abstract fun renames(): RenameDao

    companion object {
        fun create(context: Context): LumenDatabase =
            // Schema changes need a migration (schemas are exported to app/schemas, so most
            // can be Room auto-migrations). No destructive fallback: it would wipe favourites.
            Room.databaseBuilder(context, LumenDatabase::class.java, "lumen.db").build()
    }
}
