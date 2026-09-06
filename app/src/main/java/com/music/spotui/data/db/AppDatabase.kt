package com.music.spotui.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.music.spotui.data.dao.TrashBinDao
import com.music.spotui.data.entity.TrashBinEntity

@Database(entities = [TrashBinEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trashBinDao(): TrashBinDao
}
