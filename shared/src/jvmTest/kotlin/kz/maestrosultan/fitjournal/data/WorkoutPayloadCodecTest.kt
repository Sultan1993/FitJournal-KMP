package kz.maestrosultan.fitjournal.data

import kz.maestrosultan.fitjournal.data.record.payload.WorkoutExercisePayload
import kz.maestrosultan.fitjournal.data.record.payload.WorkoutPayloadCodec
import kz.maestrosultan.fitjournal.data.record.payload.WorkoutSetPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the `AWSWorkoutRecord.exercisesJson` wire contract in BOTH directions.
 *
 * This is the only irreversible surface in the workout sync path: every historical
 * blob in AppSync was written by an older client, and blobs this code writes today
 * are read by clients already in the field. Compatibility rests on two properties
 * that are otherwise implicit and untested —
 *
 *  1. `ignoreUnknownKeys = true`, so a NEW client can read an OLD payload that
 *     carries fields since removed (`difficultyType` is the live example);
 *  2. every field having a default, so an OLD client can read a NEW payload that
 *     omits a field it still expects.
 *
 * Nothing asserted either before this file existed, and `schemaVersion` is NOT a
 * safety net: it is stamped and synced but never compared anywhere in either app,
 * so it cannot gate a shape change. Adding a field with NO default is therefore
 * the genuinely unsafe edit — it breaks property 2 for every shipped client.
 */
class WorkoutPayloadCodecTest {

    /**
     * A real pre-removal payload, hardcoded rather than generated from the current
     * types so it keeps describing the OLD shape after future refactors. Carries
     * `difficultyType` (removed with the Parse decommission) plus an invented
     * `warmupFlag` standing in for any field a future client might add.
     */
    private val legacyJson = """
        [{"id":"we-1","exerciseId":"ex-1","position":0,"comment":"felt strong",
          "sets":[
            {"id":"s1","position":0,"weight":100.0,"reps":5,"difficultyType":"MEDIUM","completed":true},
            {"id":"s2","position":1,"weight":110.0,"reps":3,"difficultyType":"HARD","warmupFlag":false,"completed":true}
          ]}]
    """.trimIndent()

    @Test
    fun decodesALegacyPayloadContainingRemovedAndUnknownFields() {
        // Pull path: if this throws, every historical workout vanishes locally —
        // and on iOS an unhandled Kotlin throw crossing ObjC is a SIGABRT, not catchable.
        val exercises = WorkoutPayloadCodec.decode(legacyJson)

        assertEquals(1, exercises.size)
        val ex = exercises.single()
        assertEquals("we-1", ex.id)
        assertEquals("ex-1", ex.exerciseId)
        assertEquals("felt strong", ex.comment)
        assertEquals(2, ex.sets.size)
        assertEquals(listOf(100.0, 110.0), ex.sets.map { it.weight })
        assertEquals(listOf(5, 3), ex.sets.map { it.reps })
        assertTrue(ex.sets.all { it.completed })
    }

    @Test
    fun encodeOmitsTheRemovedFieldRatherThanWritingItBlank() {
        // Asserts the omission is real, not incidental — catch a regression here,
        // not from a sync diff.
        val json = WorkoutPayloadCodec.encode(
            listOf(
                WorkoutExercisePayload(
                    id = "we-1",
                    exerciseId = "ex-1",
                    position = 0,
                    comment = null,
                    sets = listOf(WorkoutSetPayload(id = "s1", position = 0, weight = 100.0, reps = 5)),
                )
            )
        )
        assertFalse(json.contains("difficultyType"), "difficultyType must not be written: $json")
        assertTrue(json.contains("\"id\":\"s1\""), json)
        assertTrue(json.contains("100"), json)
    }

    @Test
    fun aPayloadWrittenTodayStillDecodesWithEveryFieldOmittedButId() {
        // Old-client-reads-new-payload direction: stripped to the bare minimum,
        // every remaining field must fall back to its default rather than fail to
        // parse — the property that makes adding a non-defaulted field unsafe.
        val minimal = """[{"id":"we-1","exerciseId":"ex-1","sets":[{"id":"s1"}]}]"""
        val ex = WorkoutPayloadCodec.decode(minimal).single()
        val set = ex.sets.single()

        assertEquals(0, ex.position)
        assertEquals(null, ex.comment)
        assertEquals("s1", set.id)
        assertEquals(0, set.position)
        assertEquals(null, set.weight)
        assertEquals(null, set.reps)
        assertEquals(null, set.distance)
        assertEquals(null, set.duration)
        assertTrue(set.completed, "completed defaults to true — a logged set")
    }

    @Test
    fun roundTripsWhatTheAppActuallyWrites() {
        val original = listOf(
            WorkoutExercisePayload(
                id = "we-1", exerciseId = "ex-1", position = 0, comment = "superset A",
                sets = listOf(
                    WorkoutSetPayload(id = "s1", position = 0, weight = 82.5, reps = 8),
                    WorkoutSetPayload(id = "s2", position = 1, distance = 5.0, duration = 1800),
                ),
            )
        )
        val back = WorkoutPayloadCodec.decode(WorkoutPayloadCodec.encode(original))
        assertEquals(original, back)
    }
}
