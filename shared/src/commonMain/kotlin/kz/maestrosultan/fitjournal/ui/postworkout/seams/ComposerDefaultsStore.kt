package kz.maestrosultan.fitjournal.ui.postworkout.seams

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
