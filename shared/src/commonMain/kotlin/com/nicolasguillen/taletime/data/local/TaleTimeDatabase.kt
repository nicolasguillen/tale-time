package com.nicolasguillen.taletime.data.local

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Upsert
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

const val DATABASE_FILE_NAME = "taletime.db"

/** The play-through to pick up again, per story. [path] is `"p01,p03:1,p04b"`: node ids, a fork's choice after ':'. */
@Entity(tableName = "reading_paths")
data class ReadingPathEntity(
    @PrimaryKey val storyId: String,
    val path: String,
)

/** A page seen on any path (for the story map). */
@Entity(tableName = "visited_pages", primaryKeys = ["storyId", "nodeId"])
data class VisitedPageEntity(
    val storyId: String,
    val nodeId: String,
)

@Entity(tableName = "endings_found", primaryKeys = ["storyId", "endingId"])
data class EndingFoundEntity(
    val storyId: String,
    val endingId: String,
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM reading_paths")
    fun paths(): Flow<List<ReadingPathEntity>>

    @Query("SELECT * FROM visited_pages")
    fun visited(): Flow<List<VisitedPageEntity>>

    @Query("SELECT * FROM endings_found")
    fun endings(): Flow<List<EndingFoundEntity>>

    @Upsert
    suspend fun savePath(path: ReadingPathEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun visit(pages: List<VisitedPageEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun found(ending: EndingFoundEntity)

    @Query("DELETE FROM reading_paths")
    suspend fun deletePaths()

    @Query("DELETE FROM visited_pages")
    suspend fun deleteVisited()

    @Query("DELETE FROM endings_found")
    suspend fun deleteEndings()
}

@Database(entities = [ReadingPathEntity::class, VisitedPageEntity::class, EndingFoundEntity::class], version = 1)
@ConstructedBy(TaleTimeDatabaseConstructor::class)
abstract class TaleTimeDatabase : RoomDatabase() {
    abstract fun progress(): ProgressDao
}

// Room generates the `actual`s for each platform.
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object TaleTimeDatabaseConstructor : RoomDatabaseConstructor<TaleTimeDatabase> {
    override fun initialize(): TaleTimeDatabase
}

/** The settings shared by both platforms. Each platform supplies a builder that knows where the file goes. */
fun RoomDatabase.Builder<TaleTimeDatabase>.buildTaleTimeDatabase(): TaleTimeDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
