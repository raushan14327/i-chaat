package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.User
import com.example.data.model.Message
import com.example.data.model.FriendRequest
import com.example.data.model.Group
import com.example.data.model.GroupMember

@Database(
    entities = [User::class, Message::class, FriendRequest::class, Group::class, GroupMember::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
    abstract fun friendRequestDao(): FriendRequestDao
    abstract fun groupDao(): GroupDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chatapp_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
