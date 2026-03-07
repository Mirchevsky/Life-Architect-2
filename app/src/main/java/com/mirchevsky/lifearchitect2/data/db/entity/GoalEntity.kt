package com.mirchevsky.lifearchitect2.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a high-level goal or project.
 *
 * Each goal can contain multiple tasks and is associated with a specific user.
 * All fields have default values so goals can be created in the offline-first
 * flow without requiring Google Sign-In data to be present.
 *
 * @property id The unique identifier for the goal.
 * @property userId The ID of the user who owns this goal.
 * @property title The title of the goal.
 * @property description A detailed description of the goal.
 * @property colorHex A hex color code for display purposes.
 * @property createdAt The timestamp when the goal was created.
 */
@Entity(
    tableName = "goals",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["google_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class GoalEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "user_id", index = true)
    val userId: String = "local_user",

    val title: String = "",

    val description: String = "",

    @ColumnInfo(name = "color_hex")
    val colorHex: String = "#FFFFFF",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
