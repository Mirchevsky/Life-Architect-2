package com.mirchevsky.lifearchitect2.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mirchevsky.lifearchitect2.data.db.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the [GoalEntity] table.
 */
@Dao
interface GoalDao {

    /**
     * Inserts or updates a goal. If a goal with the same ID already exists, it is replaced.
     *
     * @param goal The goal entity to insert or update.
     */
    @Upsert
    suspend fun upsertGoal(goal: GoalEntity)

    /**
     * Observes all goals for a specific user, ordered by creation date.
     *
     * @param userId The ID of the user whose goals are to be observed.
     * @return A [Flow] emitting a list of all [GoalEntity] for the user.
     */
    @Query("SELECT * FROM goals WHERE user_id = :userId ORDER BY created_at DESC")
    fun observeGoalsForUser(userId: String): Flow<List<GoalEntity>>

    /**
     * Deletes a specific goal by its ID.
     *
     * @param goalId The ID of the goal to delete.
     */
    @Query("DELETE FROM goals WHERE id = :goalId")
    suspend fun deleteGoalById(goalId: String)
}