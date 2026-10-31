package com.sandpause.app.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One lock, from start to finish. Used for reports. */
@Entity(tableName = "lock_sessions")
data class LockSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reason: String,
    val startedAt: Long,
    val endedAt: Long = 0,
    val plannedMs: Long,
    val completed: Boolean = false,
    val endedEarly: Boolean = false,
)

/** Audit log: taps, wrong PINs, blocked apps, clock changes, service restarts. */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val type: String,
    val detail: String = "",
)

/** Per-day totals. [day] is an ISO date (yyyy-MM-dd) in the phone's time zone. */
@Entity(tableName = "daily_usage")
data class DailyUsageEntity(
    @PrimaryKey val day: String,
    val screenMs: Long = 0,
    val bonusMs: Long = 0,
    val dataBytes: Long = 0,
)

@Dao
interface LockSessionDao {
    @Insert
    suspend fun insert(entity: LockSessionEntity): Long

    @Update
    suspend fun update(entity: LockSessionEntity)

    @Query("SELECT * FROM lock_sessions WHERE id = :id")
    suspend fun byId(id: Long): LockSessionEntity?

    @Query("SELECT * FROM lock_sessions WHERE startedAt >= :from ORDER BY startedAt DESC")
    fun since(from: Long): Flow<List<LockSessionEntity>>
}

@Dao
interface EventDao {
    @Insert
    suspend fun insert(entity: EventEntity)

    @Query("SELECT * FROM events WHERE at >= :from ORDER BY at DESC LIMIT :limit")
    fun since(from: Long, limit: Int): Flow<List<EventEntity>>

    @Query("DELETE FROM events WHERE at < :before")
    suspend fun deleteBefore(before: Long)
}

@Dao
interface DailyUsageDao {
    @Query("SELECT * FROM daily_usage WHERE day = :day")
    suspend fun get(day: String): DailyUsageEntity?

    @Upsert
    suspend fun upsert(entity: DailyUsageEntity)

    @Query("SELECT * FROM daily_usage WHERE day >= :fromDay ORDER BY day ASC")
    fun since(fromDay: String): Flow<List<DailyUsageEntity>>
}

@Database(
    entities = [LockSessionEntity::class, EventEntity::class, DailyUsageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SandPauseDatabase : RoomDatabase() {
    abstract fun lockSessions(): LockSessionDao
    abstract fun events(): EventDao
    abstract fun dailyUsage(): DailyUsageDao
}
