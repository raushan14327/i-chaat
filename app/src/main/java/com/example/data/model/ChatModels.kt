package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "users")
data class User(
    @PrimaryKey val userId: String,
    val username: String,
    val bio: String = "Hey there! I am using i chat.",
    val profilePicBase64: String? = null,
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val isBlocked: Boolean = false,
    val typingToId: String? = null, // Under active typing, stores target userId
    val phoneOrEmail: String = ""
) : Serializable

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey val messageId: String,
    val senderId: String,
    val receiverId: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SENT", // PENDING, SENT, DELIVERED, READ
    val replyToId: String? = null,
    val replyToText: String? = null,
    val isDeletedForMe: Boolean = false,
    val isDeletedForEveryone: Boolean = false,
    val isForwarded: Boolean = false,
    val imageUrl: String? = null,
    val voiceUrl: String? = null,
    val voiceDurationMs: Long? = null,
    val senderReaction: String? = null,
    val receiverReaction: String? = null,
    val isGroupMessage: Boolean = false
) : Serializable

@Entity(tableName = "friend_requests")
data class FriendRequest(
    @PrimaryKey val requestId: String,
    val senderId: String,
    val senderName: String,
    val receiverId: String,
    val status: String = "PENDING" // PENDING, ACCEPTED, REJECTED
) : Serializable

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey val groupId: String,
    val name: String,
    val description: String = "",
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val groupPicBase64: String? = null
) : Serializable

@Entity(tableName = "group_members", primaryKeys = ["groupId", "userId"])
data class GroupMember(
    val groupId: String,
    val userId: String,
    val joinedAt: Long = System.currentTimeMillis()
) : Serializable
