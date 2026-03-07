package com.mirchevsky.lifearchitect2.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Represents a single avatar ownership record.
 *
 * Each row means: "the user with [userId] owns the avatar with [avatarId]".
 * The composite primary key (userId + avatarId) ensures a user cannot own
 * the same avatar twice.
 *
 * This table is designed to scale to thousands of avatars and multiple users
 * without any schema changes. Adding new avatars requires no migration —
 * new rows are simply inserted on purchase.
 *
 * Avatar ID 1 is inserted automatically when a new user is created,
 * as it is the free starter avatar.
 */
@Entity(
    tableName = "owned_avatars",
    primaryKeys = ["user_id", "avatar_id"]
)
data class OwnedAvatarEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "avatar_id")
    val avatarId: Int
)
