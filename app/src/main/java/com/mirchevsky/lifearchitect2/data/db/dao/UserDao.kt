package com.mirchevsky.lifearchitect2.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mirchevsky.lifearchitect2.data.db.entity.UserEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the [UserEntity] table.
 */
@Dao
interface UserDao {

    /**
     * Inserts or updates a user in the database. If the user already exists, it will be replaced.
     *
     * @param user The user entity to insert or update.
     */
    @Upsert
    suspend fun upsertUser(user: UserEntity)

    /**
     * Observes a single user by their unique Google ID.
     *
     * @param googleId The Google ID of the user to observe.
     * @return A [Flow] that emits the [UserEntity] whenever it changes.
     */
    @Query("SELECT * FROM users WHERE google_id = :googleId LIMIT 1")
    fun observeUser(googleId: String): Flow<UserEntity?>

    /**
     * Observes the offline-first local user as a Flow (for UI state).
     */
    @Query("SELECT * FROM users WHERE google_id = 'local_user' LIMIT 1")
    fun getLocalUser(): Flow<UserEntity?>

    /**
     * Returns the local user as a direct suspend read (not a Flow).
     *
     * Use this for one-shot reads inside coroutines where you need the result
     * immediately and cannot afford the indirection of a Flow emission. This
     * guarantees the query completes before the calling coroutine continues,
     * which is required when the result is used as a foreign key in a subsequent
     * insert.
     */
    @Query("SELECT * FROM users WHERE google_id = 'local_user' LIMIT 1")
    suspend fun getLocalUserOnce(): UserEntity?

    /**
     * Updates the theme preference for the local offline-first user.
     */
    @Query("UPDATE users SET theme_preference = :theme WHERE google_id = 'local_user'")
    suspend fun updateUserTheme(theme: String)
}
