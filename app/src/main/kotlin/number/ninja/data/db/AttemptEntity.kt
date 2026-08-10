package number.ninja.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attempts")
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    /** [number.ninja.domain.Operation.name] */
    val operation: String,
    /** [number.ninja.domain.Level.name] */
    val level: String,
    /** Left-hand operand of the fact being practiced (e.g. the multiplicand). */
    val operandA: Int,
    /** Right-hand operand (e.g. the multiplier) — used for weak-spot detection such as "×7-9". */
    val operandB: Int,
    val correct: Boolean,
)
