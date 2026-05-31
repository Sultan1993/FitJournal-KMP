package kz.maestrosultan.fitjournal.domain.exercise

/**
 * Muscle-group catalog row. Read-only on clients — the catalog is
 * admin-seeded via `scripts/seed_aws_global_catalog.py` and pulled to
 * SQLite by the SyncOrchestrator. There is no client-side `create`
 * path, so `remoteId` is always populated by the time a `Category`
 * reaches the domain layer (it's NOT NULL in the SQLite schema).
 *
 * `uuid` is the local SQLite primary key; `remoteId` is the AWS object
 * id. Kept as separate fields rather than uuid-IS-the-id (which the
 * exercises table uses) for legacy parity with the Parse catalog.
 */
data class Category(
    val uuid: String,
    val remoteId: String,
    val name: String,
    val type: CategoryType,
    val details: String?,
)
