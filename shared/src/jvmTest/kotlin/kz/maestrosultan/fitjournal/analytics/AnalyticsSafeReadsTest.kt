package kz.maestrosultan.fitjournal.analytics

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository

class AnalyticsSafeReadsTest {

    private val date = LocalDate(2026, 5, 10)

    private fun repoReturning(read: () -> Boolean): RecordRepository =
        object : RecordRepository by unsupported() {
            override suspend fun hasLiveRecordInWorkout(
                userId: String, journalId: String, date: LocalDate, workoutNumber: Int,
            ): Boolean = read()
        }

    @Test
    fun passesTheAnswerThrough(): Unit = runBlocking {
        assertEquals(false, hadLiveRecordForAnalytics(repoReturning { false }, "u", "j", date, 1))
        assertEquals(true, hadLiveRecordForAnalytics(repoReturning { true }, "u", "j", date, 1))
    }

    @Test
    fun anyThrowable_degradesToTrue(): Unit = runBlocking {
        assertEquals(true, hadLiveRecordForAnalytics(repoReturning { throw IllegalStateException("locked") }, "u", "j", date, 1))
        assertEquals(true, hadLiveRecordForAnalytics(repoReturning { throw OutOfMemoryError() }, "u", "j", date, 1))
    }

    @Test
    fun cancellation_isRethrown(): Unit = runBlocking {
        assertFailsWith<CancellationException> {
            hadLiveRecordForAnalytics(repoReturning { throw CancellationException("cancelled") }, "u", "j", date, 1)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun unsupported(): RecordRepository =
        java.lang.reflect.Proxy.newProxyInstance(
            RecordRepository::class.java.classLoader,
            arrayOf(RecordRepository::class.java),
        ) { _, _, _ -> error("not used") } as RecordRepository
}
