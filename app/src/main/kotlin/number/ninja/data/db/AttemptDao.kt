package number.ninja.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttemptDao {
    @Insert
    suspend fun insert(attempt: AttemptEntity)

    @Query("SELECT * FROM attempts ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<AttemptEntity>>

    @Query("DELETE FROM attempts")
    suspend fun clear()
}
