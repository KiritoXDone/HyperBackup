package io.github.kiritoxdone.hyperbackup.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "destinations") data class DestinationRow(@PrimaryKey val id: String, val json: String, val encryptedSecret: String)
@Entity(tableName = "repository_cache") data class RepositoryRow(@PrimaryKey val destinationId: String, val json: String, val scannedAt: Long)
@Entity(tableName = "task_cache") data class TaskRow(@PrimaryKey val id: String, val json: String, val startedAt: Long)
@Entity(tableName = "submissions") data class SubmissionRow(@PrimaryKey val taskId: String, val json: String, val planJson: String)

@Dao interface BackupDao {
    @Query("SELECT * FROM destinations ORDER BY id") suspend fun destinations(): List<DestinationRow>
    @Upsert suspend fun saveDestination(row: DestinationRow)
    @Query("DELETE FROM destinations WHERE id = :id") suspend fun removeDestination(id: String)
    @Upsert suspend fun saveRepository(row: RepositoryRow)
    @Query("SELECT * FROM repository_cache WHERE destinationId = :id") suspend fun repository(id: String): RepositoryRow?
    @Upsert suspend fun saveTask(row: TaskRow)
    @Query("SELECT * FROM task_cache ORDER BY startedAt DESC LIMIT 500") suspend fun tasks(): List<TaskRow>
    @Upsert suspend fun saveSubmission(row: SubmissionRow)
    @Query("SELECT * FROM submissions") suspend fun submissions(): List<SubmissionRow>
    @Query("DELETE FROM submissions WHERE taskId = :id") suspend fun accepted(id: String)
}

@Database(entities = [DestinationRow::class, RepositoryRow::class, TaskRow::class, SubmissionRow::class], version = 1, exportSchema = true)
abstract class BackupDatabase : RoomDatabase() {
    abstract fun dao(): BackupDao
    companion object {
        fun open(context: Context) = Room.databaseBuilder(context, BackupDatabase::class.java, "hyperbackup.db").build()
    }
}
