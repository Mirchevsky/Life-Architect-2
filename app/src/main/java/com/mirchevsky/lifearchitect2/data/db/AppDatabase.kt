package com.mirchevsky.lifearchitect2.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mirchevsky.lifearchitect2.data.db.dao.GoalDao
import com.mirchevsky.lifearchitect2.data.db.dao.TaskDao
import com.mirchevsky.lifearchitect2.data.db.dao.UserDao
import com.mirchevsky.lifearchitect2.data.db.entity.GoalEntity
import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity
import com.mirchevsky.lifearchitect2.data.db.entity.UserEntity

/**
 * Version history:
 *   1  — Initial schema
 *   2  — Added task completion fields
 *   3  — Added OwnedAvatarEntity; replaced ownedAvatarIds/selectedAvatarId with equippedAvatarId
 *   4  — Replaced OwnedAvatarEntity with OwnedPartEntity; replaced equippedAvatarId with avatarConfig string
 *   5  — Removed OwnedPartEntity and coins column (shop system decommissioned)
 *   6  — Added streak and daily task count columns to users table
 *   7  — Added weekly and monthly streak claim flags to users table
 *   8  — Added nullable dueDate column to tasks table
 *   9  — Removed FOREIGN KEY constraints from tasks table (single-user offline app; constraints caused crashes)
 *   10 — Added is_pinned and is_urgent boolean columns to tasks table
 */
@Database(
    entities = [
        UserEntity::class,
        GoalEntity::class,
        TaskEntity::class
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun goalDao(): GoalDao
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Migration v5 → v6: adds streak and daily counter columns to users table. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE users ADD COLUMN daily_streak INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE users ADD COLUMN last_completion_day INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE users ADD COLUMN tasks_completed_today INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE users ADD COLUMN today_reset_day INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Migration v6 → v7: adds weekly and monthly streak claim flags. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE users ADD COLUMN weekly_streak_claimed INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE users ADD COLUMN monthly_milestone_claimed INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Migration v7 → v8: adds nullable due_date column to tasks table. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN due_date INTEGER")
            }
        }

        /**
         * Migration v8 → v9: removes FOREIGN KEY constraints from the tasks table.
         *
         * SQLite does not support DROP CONSTRAINT, so we recreate the table without
         * the constraints and copy all existing data across.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Create the new table without foreign keys
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tasks_new` (
                        `id` TEXT NOT NULL,
                        `goal_id` TEXT NOT NULL DEFAULT '',
                        `user_id` TEXT NOT NULL DEFAULT 'local_user',
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL DEFAULT '',
                        `category` TEXT NOT NULL DEFAULT '',
                        `status` TEXT NOT NULL DEFAULT 'pending',
                        `is_completed` INTEGER NOT NULL DEFAULT 0,
                        `difficulty` TEXT NOT NULL,
                        `prerequisites` TEXT NOT NULL DEFAULT '[]',
                        `created_at` INTEGER NOT NULL DEFAULT 0,
                        `completed_at` INTEGER,
                        `due_date` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                // 2. Copy all existing data
                database.execSQL("""
                    INSERT INTO `tasks_new`
                    SELECT `id`, `goal_id`, `user_id`, `title`, `description`, `category`,
                           `status`, `is_completed`, `difficulty`, `prerequisites`,
                           `created_at`, `completed_at`, `due_date`
                    FROM `tasks`
                """.trimIndent())

                // 3. Drop the old table
                database.execSQL("DROP TABLE `tasks`")

                // 4. Rename the new table
                database.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")

                // 5. Recreate the indexes
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_goal_id` ON `tasks` (`goal_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_user_id` ON `tasks` (`user_id`)")
            }
        }

        /**
         * Migration v9 → v10: adds is_pinned and is_urgent boolean columns to tasks table.
         *
         * Both default to 0 (false) so all existing rows are unaffected.
         * SQLite stores Kotlin Booleans as INTEGER (0/1); Room handles the mapping automatically.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE tasks ADD COLUMN is_urgent INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "life_architect_database"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
