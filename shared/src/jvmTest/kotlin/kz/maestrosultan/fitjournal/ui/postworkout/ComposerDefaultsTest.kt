package kz.maestrosultan.fitjournal.ui.postworkout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kz.maestrosultan.fitjournal.ui.postworkout.composer.BackdropKind
import kz.maestrosultan.fitjournal.ui.postworkout.composer.BlockTransform
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ComposerDefaults
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareLayoutKind
import kz.maestrosultan.fitjournal.ui.postworkout.composer.StatKind

/**
 * JSON round-trip contract for [ComposerDefaults] — what
 * ComposerDefaultsStore implementations persist. Corrupt stored JSON must
 * surface as a [SerializationException] (store implementations catch it and
 * load as null).
 */
class ComposerDefaultsTest {

    @Test
    fun jsonRoundTrip_preservesEveryField() {
        val original = ComposerDefaults(
            layout = ShareLayoutKind.Receipt,
            backdropKind = BackdropKind.Photo,
            statsPick = listOf(StatKind.Duration, StatKind.BestSet, StatKind.TotalReps),
            scrim = 0.35f,
            transform = BlockTransform(cx = 0.5f, cy = 0.25f, scale = 1.2f, rotationDeg = -12.5f),
            blockRemoved = false,
        )

        val decoded = Json.decodeFromString<ComposerDefaults>(Json.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun jsonRoundTrip_nullTransform_and_blockRemovedTrue() {
        val original = ComposerDefaults(
            layout = ShareLayoutKind.NewBest,
            backdropKind = BackdropKind.Transparent,
            statsPick = emptyList(),
            scrim = 0.0f,
            transform = null,
            blockRemoved = true,
        )

        val decoded = Json.decodeFromString<ComposerDefaults>(Json.encodeToString(original))

        assertEquals(original, decoded)
        assertNull(decoded.transform)
        assertTrue(decoded.blockRemoved)
    }

    @Test
    fun corruptJson_truncatedDocument_failsToDecode() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString<ComposerDefaults>("""{"layout":"Stats","backdropKind":""")
        }
    }

    @Test
    fun corruptJson_unknownEnumValue_failsToDecode() {
        val corrupt =
            """{"layout":"Diagonal","backdropKind":"Photo","statsPick":[],""" +
                """"scrim":0.0,"transform":null,"blockRemoved":false}"""

        assertFailsWith<SerializationException> {
            Json.decodeFromString<ComposerDefaults>(corrupt)
        }
    }
}
