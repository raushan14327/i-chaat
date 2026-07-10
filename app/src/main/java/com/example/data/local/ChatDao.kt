package com.example.data.local

import androidx.room.*
import com.example.data.model.User
import com.example.data.model.Message
import com.example.data.model.FriendRequest
import com.example.data.model.Group
import com.example.data.model.GroupMember
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE userId = :userId")
    fun getUserFlow(userId: String): Flow<User?>

    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUserById(userId: String): User?

    @Query("SELECT * FROM users")
    fun getAllUsersFlow(): Flow<List<User>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>)

    @Update
    suspend fun updateUser(user: User)

    @Query("""
        SELECT * FROM users 
        WHERE userId IN (
            SELECT senderId FROM friend_requests WHERE receiverId = :myId AND status = 'ACCEPTED'
            UNION
            SELECT receiverId FROM friend_requests WHERE senderId = :myId AND status = 'ACCEPTED'
        )
    """)
    fun getFriendsFlow(myId: String): Flow<List<User>>
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE (senderId = :myId AND receiverId = :otherId AND isDeletedForMe = 0) OR (senderId = :otherId AND receiverId = :myId AND isDeletedForMe = 0) ORDER BY timestamp ASC")
    fun getMessagesFlow(myId: String, otherId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE messageId = :messageId")
    suspend fun getMessageById(messageId: String): Message?

    @Query("SELECT * FROM messages WHERE (senderId = :otherId AND receiverId = :myId AND status != 'READ')")
    suspend fun getUnreadMessages(myId: String, otherId: String): List<Message>

    @Query("SELECT COUNT(*) FROM messages WHERE senderId = :senderId AND receiverId = :receiverId AND status != 'READ' AND isDeletedForMe = 0")
    fun getUnreadCountFlow(senderId: String, receiverId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Query("SELECT * FROM messages WHERE receiverId = :groupId AND isGroupMessage = 1 AND isDeletedForMe = 0 ORDER BY timestamp ASC")
    fun getGroupMessagesFlow(groupId: String): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<Message>)

    @Update
    suspend fun updateMessage(message: Message)

    @Query("UPDATE messages SET status = 'READ' WHERE senderId = :senderId AND receiverId = :receiverId AND status != 'READ'")
    suspend fun markMessagesAsRead(senderId: String, receiverId: String)
}

@Dao
interface FriendRequestDao {
    @Query("SELECT * FROM friend_requests WHERE receiverId = :receiverId AND status = 'PENDING'")
    fun getPendingRequestsFlow(receiverId: String): Flow<List<FriendRequest>>

    @Query("SELECT * FROM friend_requests WHERE (senderId = :userId OR receiverId = :userId)")
    fun getAllRequestsFlow(userId: String): Flow<List<FriendRequest>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: FriendRequest)

    @Update
    suspend fun updateRequest(request: FriendRequest)

    @Query("DELETE FROM friend_requests WHERE requestId = :requestId")
    suspend fun deleteRequestById(requestId: String)

    @Query("SELECT * FROM friend_requests WHERE (senderId = :user1 AND receiverId = :user2) OR (senderId = :user2 AND receiverId = :user1) LIMIT 1")
    suspend fun getRequestBetweenUsers(user1: String, user2: String): FriendRequest?
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups WHERE groupId = :groupId")
    suspend fun getGroupById(groupId: String): Group?

    @Query("SELECT * FROM groups")
    fun getAllGroupsFlow(): Flow<List<Group>>

    @Query("""
        SELECT * FROM groups 
        WHERE groupId IN (SELECT groupId FROM group_members WHERE userId = :userId)
    """)
    fun getMyGroupsFlow(userId: String): Flow<List<Group>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: Group)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMember(member: GroupMember)

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND userId = :userId")
    suspend fun removeGroupMember(groupId: String, userId: String)

    @Query("SELECT COUNT(*) FROM group_members WHERE groupId = :groupId AND userId = :userId")
    suspend fun isUserInGroup(groupId: String, userId: String): Int

    @Query("""
        SELECT * FROM users 
        WHERE userId IN (SELECT userId FROM group_members WHERE groupId = :groupId)
    """)
    fun getGroupMembers(groupId: String): Flow<List<User>>
}
