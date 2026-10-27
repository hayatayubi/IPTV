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
    ],
    version = 4,
    // Room compares the exported schema JSONs and writes the SQL itself.
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // adds the guide tables
        AutoMigration(from = 2, to = 3), // adds the favourites table
        AutoMigration(from = 3, to = 4), // adds hidden_groups and sources.hidden
    ],
)
abstract class LumenDatabase : RoomDatabase() {
    abstract fun sources(): SourceDao
    abstract fun channels(): ChannelDao
    abstract fun epg(): EpgDao
    abstract fun favourites(): FavouriteDao
    abstract fun hidden(): HiddenDao

    companion object {
        fun create(context: Context): LumenDatabase =
            // Schema changes need a migration (schemas are exported to app/schemas, so most
            // can be Room auto-migrations). No destructive fallback: it would wipe favourites.
            Room.databaseBuilder(context, LumenDatabase::class.java, "lumen.db").build()
    }
}
