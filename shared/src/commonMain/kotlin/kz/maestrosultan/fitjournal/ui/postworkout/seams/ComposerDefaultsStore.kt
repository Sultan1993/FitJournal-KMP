package kz.maestrosultan.fitjournal.ui.postworkout.seams

import kotlinx.serialization.json.Json
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ComposerDefaults

/**
 * Persistence for the last-used composer setup so the next share starts where
 * the user left off. Implementations serialize [ComposerDefaults] as JSON into
 * platform key-value storage; a missing or corrupt entry loads as null (never
 * throws) and the composer falls back to its built-in defaults.
 */
interface ComposerDefaultsStore {
    suspend fun load(): ComposerDefaults?
    suspend fun save(defaults: ComposerDefaults)
}

/**
 * The platform half of [ComposerDefaultsStore]: one string slot, nothing else.
 * Android backs it with DataStore, iOS with `UserDefaults` — neither needs to
 * know what the string contains.
 */
interface ComposerDefaultsStorage {
    suspend fun read(): String?
    suspend fun write(json: String)
}

/**
 * The whole of the defaults contract except the bytes: encoding, decoding, and
 * the rule that nothing here may ever throw.
 *
 * It lives in shared rather than once per platform because the failure
 * behaviour is the part that is easy to get subtly different — and a composer
 * that crashes on a stale preference from a previous app version would be a
 * launch-blocking bug reachable only by upgrading, i.e. never seen in testing.
 * A corrupt or outdated entry loads as null and the composer falls back to its
 * built-in defaults; a failed write is dropped, because losing a remembered
 * layout is not worth surfacing to someone who just finished a workout.
 */
class SerializedComposerDefaultsStore(
    private val storage: ComposerDefaultsStorage,
) : ComposerDefaultsStore {

    override suspend fun load(): ComposerDefaults? = runCatching {
        storage.read()?.let { Json.decodeFromString<ComposerDefaults>(it) }
    }.getOrNull()

    override suspend fun save(defaults: ComposerDefaults) {
        runCatching { storage.write(Json.encodeToString(defaults)) }
    }
}
