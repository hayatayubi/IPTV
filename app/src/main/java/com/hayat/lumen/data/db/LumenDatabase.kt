package com.hayat.lumen.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SourceEntity::class, ChannelGroupEntity::class, ChannelEntity::class],
    version = 1,
)
abstract class LumenDatabase : RoomDatabase() {
    abstract fun sources(): SourceDao
    abstract fun channels(): ChannelDao

    companion object {
        fun create(context: Context): LumenDatabase =
            // Schema changes need a migration (schemas are exported to app/schemas, so most
            // can be Room auto-migrations). No destructive fallback: it would wipe favourites.
            Room.databaseBuilder(context, LumenDatabase::class.java, "lumen.db").build()
    }
}
