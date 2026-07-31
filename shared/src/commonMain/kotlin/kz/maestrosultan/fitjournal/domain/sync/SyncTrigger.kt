package kz.maestrosultan.fitjournal.domain.sync

/**
 * The single entry point a mutating use case calls after a local write, so
 * `SyncOrchestrator` drains `pendingUpload=1` rows promptly. [reason] is logged
 * on `[FJ_SYNC]` lines for traceability.
 *
 * The interface is shared (KMP); the implementation is platform-specific and
 * stays app-side — Android's `WorkManagerSyncTrigger` enqueues a unique
 * one-time `SyncWorker`, iOS's `BackgroundTaskSyncTrigger` runs a detached
 * `Task`. `requestTick` is fire-and-forget (non-suspend): it schedules a tick
 * and returns; it never awaits the sync.
 *
 * Moved here so use cases can live in KMP (written once, tested once) instead of
 * being mirrored per platform — the parity that this codebase's offline-first
 * bugs kept eroding. SKIE exposes this as an ObjC protocol the Swift trigger
 * conforms to. `SyncReason` already lived here.
 */
interface SyncTrigger {
    fun requestTick(reason: SyncReason)
}
