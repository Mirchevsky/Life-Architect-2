package com.mirchevsky.lifearchitect2.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a single task or "quest" in the database.
 *
 * Foreign key constraints have been intentionally removed. The app uses a single
 * offline-first local user ("local_user") and the constraints provided no safety
 * benefit while causing FOREIGN KEY constraint crashes when the user row had not
 * yet been written at the time of the first task insert.
 *
 * @property id The unique identifier for the task.
 * @property goalId The ID of the goal this task belongs to.
 * @property userId The ID of the user who owns this task.
 * @property title The title of the task.
 * @property description A detailed description of the task.
 * @property category The category of the task (e.g., Habits, Energy).
 * @property status The current status of the task (e.g., pending, completed).
 * @property isCompleted Convenience boolean, true when status == "completed".
 * @property difficulty The difficulty level of the task, used for calculating XP.
 * @property prerequisites A list of sub-tasks that must be completed before this task.
 * @property createdAt The timestamp when the task was created.
 * @property completedAt The timestamp when the task was completed.
 * @property dueDate The timestamp for the task's deadline.
 * @property isPinned When true the task is pinned to the top of the list and its title is shown in amber/yellow.
 * @property isUrgent When true the task is marked urgent and its title is shown in red.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "goal_id", index = true)
    val goalId: String = "",

    @ColumnInfo(name = "user_id", index = true)
    val userId: String = "local_user",

    val title: String,

    val description: String = "",

    val category: String = "",

    val status: String = "pending",

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,

    val difficulty: String,

    val prerequisites: List<Prerequisite> = emptyList(),

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    @ColumnInfo(name = "due_date")
    val dueDate: Long? = null,

    /** When true, this task floats to the top of the list and its title is rendered in amber/yellow. */
    @ColumnInfo(name = "is_pinned", defaultValue = "0")
    val isPinned: Boolean = false,

    /** When true, this task is marked as urgent and its title is rendered in red. */
    @ColumnInfo(name = "is_urgent", defaultValue = "0")
    val isUrgent: Boolean = false
)

/**
 * Represents a single prerequisite (sub-task) for a parent TaskEntity.
 * This is an embedded data class, not a separate table.
 */
data class Prerequisite(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val completed: Boolean = false,
    val completedAt: Long? = null
)
