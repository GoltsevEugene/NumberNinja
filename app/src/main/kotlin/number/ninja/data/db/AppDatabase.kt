package number.ninja.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AttemptEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun attemptDao(): AttemptDao

    companion object {
        const val DATABASE_NAME = "number_ninja.db"
    }
}
