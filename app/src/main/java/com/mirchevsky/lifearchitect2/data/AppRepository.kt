package com.mirchevsky.lifearchitect2.data

import com.mirchevsky.lifearchitect2.data.db.dao.GoalDao
import com.mirchevsky.lifearchitect2.data.db.dao.TaskDao
import com.mirchevsky.lifearchitect2.data.db.dao.UserDao
import com.mirchevsky.lifearchitect2.data.db.entity.GoalEntity
import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity
import com.mirchevsky.lifearchitect2.data.db.entity.UserEntity
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val userDao: UserDao,
    private val goalDao: GoalDao,
    private val taskDao: TaskDao
) {

    // --- User ---

    fun observeUser(googleId: String): Flow<UserEntity?> = userDao.observeUser(googleId)

    /** Flow-based user observation — use for UI state only. */
    fun getUser(): Flow<UserEntity?> = userDao.getLocalUser()

    /**
     * Direct suspend read of the local user — use inside coroutines where the
     * result is needed immediately (e.g. before a foreign-key insert).
     */
    suspend fun getUserOnce(): UserEntity? = userDao.getLocalUserOnce()

    suspend fun upsertUser(user: UserEntity) = userDao.upsertUser(user)
    suspend fun updateUser(user: UserEntity) = upsertUser(user)
    suspend fun updateUserTheme(theme: Theme) = userDao.updateUserTheme(theme.name)

    // --- Goals ---

    fun observeGoalsForUser(userId: String): Flow<List<GoalEntity>> = goalDao.observeGoalsForUser(userId)

    // --- Tasks ---

    fun observeTasksForUser(userId: String): Flow<List<TaskEntity>> = taskDao.observeTasksForUser(userId)
    fun observePendingTasksForUser(userId: String): Flow<List<TaskEntity>> = taskDao.observePendingTasksForUser(userId)
    fun getCompletedTasks(): Flow<List<TaskEntity>> = taskDao.observeCompletedTasksForUser("local_user")
    fun getAllTasks(): Flow<List<TaskEntity>> = observeTasksForUser("local_user")
    suspend fun upsertTask(task: TaskEntity) = taskDao.upsertTask(task)
    suspend fun insertTask(task: TaskEntity) = upsertTask(task)
    suspend fun updateTask(task: TaskEntity) = upsertTask(task)
    suspend fun deleteTask(taskId: String) = taskDao.deleteTaskById(taskId)
}
