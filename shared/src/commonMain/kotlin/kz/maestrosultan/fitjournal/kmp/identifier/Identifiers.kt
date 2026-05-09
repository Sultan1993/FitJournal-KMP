package kz.maestrosultan.fitjournal.kmp.identifier

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Single source of truth for new entity identifiers across both apps.
 * Returns an RFC 4122 v4 UUID in the canonical 8-4-4-4-12 hex form
 * (e.g. `550e8400-e29b-41d4-a716-446655440000`) — byte-for-byte
 * identical to `Foundation.UUID().uuidString` and
 * `java.util.UUID.randomUUID().toString()`.
 *
 * SKIE exposes this to Swift as `IdentifiersKt.randomUuid()`. (Named
 * `randomUuid` — not `newUuid` — because Kotlin/Native rewrites `new*` to
 * `doNew*` in the generated Obj-C/Swift bindings to avoid colliding with
 * Obj-C's `new` selector convention.)
 */
@OptIn(ExperimentalUuidApi::class)
fun randomUuid(): String = Uuid.random().toString()
