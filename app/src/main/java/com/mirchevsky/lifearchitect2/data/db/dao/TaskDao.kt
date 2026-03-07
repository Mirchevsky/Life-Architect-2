package com.mirchevsky.lifearchitect2.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the [TaskEntity] table.
 */
@Dao
interface TaskDao {

    /**
     * Inserts or updates a task. If a task with the same ID already exists, it is replaced.
     *
     * @param task The task entity to insert or update.
     */
    @Upsert
    suspend fun upsertTask(task: TaskEntity)

    /**
     * Observes all tasks for a specific user, ordered by creation date descending.
     *
     * @param userId The ID of the user whose tasks are to be observed.
     * @return A [Flow] emitting a list of all [TaskEntity] for the user.
     */
    @Query("SELECT * FROM tasks WHERE user_id = :userId ORDER BY created_at DESC")
    fun observeTasksForUser(userId: String): Flow<List<TaskEntity>>

    /**
     * Observes all pending (not completed) tasks for a specific user.
     * Filters on the [TaskEntity.isCompleted] boolean column for reliability.
     *
     * @param userId The ID of the user whose pending tasks are to be observed.
     * @return A [Flow] emitting a list of pending [TaskEntity] for the user.
     */
    @Query("SELECT * FROM tasks WHERE user_id = :userId AND is_completed = 0 ORDER BY created_at DESC")
    fun observePendingTasksForUser(userId: String): Flow<List<TaskEntity>>

    /**
     * Observes all completed tasks for a specific user, ordered by completion date descending.
     *
     * @param userId The ID of the user whose completed tasks are to be observed.
     * @return A [Flow] emitting a list of completed [TaskEntity] for the user.
     */
    @Query("SELECT * FROM tasks WHERE user_id = :userId AND is_completed = 1 ORDER BY completed_at DESC")
    fun observeCompletedTasksForUser(userId: String): Flow<List<TaskEntity>>

    /**
     * Deletes a specific task by its ID.
     *
     * @param taskId The ID of the task to delete.
     */
    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: String)
}
