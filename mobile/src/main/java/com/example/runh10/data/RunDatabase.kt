package com.example.runh10.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Cached aggregates of one run, computed once at ingest (sync or phone-recorded).
 * Series columns are compact JSON — plenty for a personal app, keeps Room simple.
 */
@Entity(tableName = "run_summary")
data class RunSummaryEntity(
    @PrimaryKey val sessionId: String,
    val name: String,
    val workoutType: String,        // RUN | TRAIL | TRACK
    val source: String,             // watch | phone
    val startMs: Long,
    val endMs: Long,
    val distanceM: Double,
    val elapsedMs: Long,
    val movingMs: Long,
    val avgBpm: Int?,
    val maxBpm: Int?,
    val hrvMs: Double?,
    val kcal: Double?,
    val elevGainM: Double,
    val zoneMillisJson: String,     // [z1..z5] ms
    val splitsJson: String,         // [SplitJson]
    val routeJson: String,          // [[lat,lon],...] downsampled
    val hrSeriesJson: String,       // [[offsetSec,bpm],...] downsampled
    val feel: String?,              // easy | steady | hard | max
    val hcPending: Boolean = false, // phone-recorded run whose Health Connect write failed; retry on resume
)

@Serializable
data class SplitJson(
    val index: Int,
    val distanceM: Double,
    val movingMs: Long,
    val avgPaceMps: Double,
    val avgBpm: Int? = null,
    val elevGainM: Double = 0.0,
)

object RunJson {
    val json = Json { ignoreUnknownKeys = true }
    private val splitList = ListSerializer(SplitJson.serializer())
    private val doublePairs = ListSerializer(ListSerializer(Double.serializer()))

    fun encodeSplits(s: List<SplitJson>): String = json.encodeToString(splitList, s)
    fun decodeSplits(s: String): List<SplitJson> = runCatching { json.decodeFromString(splitList, s) }.getOrDefault(emptyList())
    fun encodePairs(p: List<List<Double>>): String = json.encodeToString(doublePairs, p)
    fun decodePairs(s: String): List<List<Double>> = runCatching { json.decodeFromString(doublePairs, s) }.getOrDefault(emptyList())
    fun encodeLongs(l: List<Long>): String = l.joinToString(",", "[", "]")
    fun decodeLongs(s: String): List<Long> =
        runCatching { s.trim('[', ']').split(',').filter { it.isNotBlank() }.map { it.trim().toLong() } }
            .getOrDefault(listOf(0L, 0L, 0L, 0L, 0L))
}

@Dao
interface RunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(run: RunSummaryEntity)

    @Query("SELECT * FROM run_summary ORDER BY startMs DESC")
    fun observeAll(): Flow<List<RunSummaryEntity>>

    @Query("SELECT * FROM run_summary WHERE sessionId = :id")
    fun observeById(id: String): Flow<RunSummaryEntity?>

    @Query("SELECT * FROM run_summary WHERE sessionId = :id")
    suspend fun byId(id: String): RunSummaryEntity?

    @Query("UPDATE run_summary SET name = :name, feel = :feel WHERE sessionId = :id")
    suspend fun updateNameFeel(id: String, name: String, feel: String?)

    @Query("SELECT * FROM run_summary WHERE startMs >= :fromMs ORDER BY startMs DESC")
    fun observeSince(fromMs: Long): Flow<List<RunSummaryEntity>>

    @Query("DELETE FROM run_summary WHERE sessionId = :id")
    suspend fun delete(id: String)

    @Query("SELECT sessionId FROM run_summary")
    suspend fun allIds(): List<String>

    @Query("SELECT * FROM run_summary WHERE hcPending = 1")
    suspend fun pendingHc(): List<RunSummaryEntity>

    @Query("UPDATE run_summary SET hcPending = :pending WHERE sessionId = :id")
    suspend fun markHcPending(id: String, pending: Boolean)

    @Query("SELECT * FROM run_summary WHERE kcal IS NULL")
    suspend fun missingKcal(): List<RunSummaryEntity>

    @Query("UPDATE run_summary SET kcal = :kcal WHERE sessionId = :id")
    suspend fun updateKcal(id: String, kcal: Double)
}

/** v1 -> v2: add hcPending (F5 — retry Health Connect writes that failed for phone-recorded runs). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE run_summary ADD COLUMN hcPending INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(entities = [RunSummaryEntity::class], version = 2, exportSchema = false)
abstract class RunDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao

    companion object {
        @Volatile private var instance: RunDatabase? = null
        fun get(context: Context): RunDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, RunDatabase::class.java, "hrbridge.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
