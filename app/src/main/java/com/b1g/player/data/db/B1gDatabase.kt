package com.b1g.player.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChannelEntity::class, VodEntity::class, SourceMetaEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class B1gDatabase : RoomDatabase() {

    abstract fun contentDao(): ContentDao

    companion object {
        fun create(context: Context): B1gDatabase =
            Room.databaseBuilder(context, B1gDatabase::class.java, "b1g-content.db")
                // The contents are a cache of someone else's playlist: throwing it
                // away and re-downloading beats shipping migrations for it.
                .fallbackToDestructiveMigration()
                .build()
    }
}
