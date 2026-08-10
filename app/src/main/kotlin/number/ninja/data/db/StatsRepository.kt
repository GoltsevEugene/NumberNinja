package number.ninja.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import number.ninja.domain.Level
import number.ninja.domain.LevelStats
import number.ninja.domain.MathExample
import number.ninja.domain.Operation
import number.ninja.domain.OperationStats
import number.ninja.domain.WeakSpot

class StatsRepository(private val dao: AttemptDao) {

    suspend fun recordAttempt(example: MathExample, correct: Boolean, timestampMillis: Long) {
        val secondOperand = example.terms.firstOrNull()?.value ?: 0
        dao.insert(
            AttemptEntity(
                timestampMillis = timestampMillis,
                operation = example.operation.name,
                level = example.level.name,
                operandA = example.initial,
                operandB = secondOperand,
                correct = correct,
            )
        )
    }

    fun observeOperationStats(): Flow<List<OperationStats>> = dao.observeAll().map { attempts ->
        Operation.entries.map { op ->
            val forOp = attempts.filter { it.operation == op.name }
            OperationStats(op, forOp.size, forOp.count { it.correct })
        }
    }

    /**
     * Multiplication/division are bucketed by the larger operand into fact-table ranges (e.g.
     * "×7-9"). Addition/subtraction have no natural operand-range bucket, so they're bucketed by
     * [Level] instead. In addition, an unbucketed whole-operation weak spot is always included
     * (using all attempts for that operation, no sub-bucketing) so a low-accuracy operation can
     * never fail to appear here just because its attempts are spread thin across buckets — that
     * would otherwise contradict the operation-level accuracy shown elsewhere (e.g. a stats tile
     * with no minimum-attempts gate).
     */
    fun observeWeakSpots(): Flow<List<WeakSpot>> = dao.observeAll().map { attempts ->
        val rangeBuckets = listOf(2..3, 4..6, 7..9, 10..10)
        val rangeBased = listOf(Operation.MULTIPLICATION, Operation.DIVISION).flatMap { op ->
            val forOp = attempts.filter { it.operation == op.name }
            rangeBuckets.mapNotNull { range ->
                val inBucket = forOp.filter { maxOf(it.operandA, it.operandB) in range }
                flagIfWeak(inBucket.size, inBucket.count { it.correct }) { accuracy, count ->
                    WeakSpot(op, accuracy, count, rangeLabel = rangeLabel(range))
                }
            }
        }
        val levelBased = listOf(Operation.ADDITION, Operation.SUBTRACTION).flatMap { op ->
            val forOp = attempts.filter { it.operation == op.name }
            Level.entries.mapNotNull { level ->
                val inLevel = forOp.filter { it.level == level.name }
                flagIfWeak(inLevel.size, inLevel.count { it.correct }) { accuracy, count ->
                    WeakSpot(op, accuracy, count, level = level)
                }
            }
        }
        val operationBased = Operation.entries.mapNotNull { op ->
            val forOp = attempts.filter { it.operation == op.name }
            flagIfWeak(forOp.size, forOp.count { it.correct }) { accuracy, count ->
                WeakSpot(op, accuracy, count)
            }
        }
        rangeBased + levelBased + operationBased
    }

    fun observeLevelStats(operation: Operation): Flow<List<LevelStats>> = dao.observeAll().map { attempts ->
        val forOp = attempts.filter { it.operation == operation.name }
        Level.entries.map { level ->
            val inLevel = forOp.filter { it.level == level.name }
            LevelStats(level, inLevel.size, inLevel.count { it.correct })
        }
    }

    private inline fun flagIfWeak(attempts: Int, correct: Int, make: (accuracy: Int, attempts: Int) -> WeakSpot): WeakSpot? {
        if (attempts < WeakSpot.MIN_ATTEMPTS_TO_FLAG) return null
        val accuracy = (correct * 100) / attempts
        if (accuracy >= WeakSpot.ACCURACY_THRESHOLD_PERCENT) return null
        return make(accuracy, attempts)
    }

    private fun rangeLabel(range: IntRange): String = if (range.first == range.last) "${range.first}" else "${range.first}-${range.last}"
}
