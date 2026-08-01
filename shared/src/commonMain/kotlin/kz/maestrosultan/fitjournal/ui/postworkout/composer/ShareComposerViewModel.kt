package kz.maestrosultan.fitjournal.ui.postworkout.composer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kz.maestrosultan.fitjournal.domain.workout.summary.SessionSummary
import kz.maestrosultan.fitjournal.ui.postworkout.PostWorkoutContext
import kz.maestrosultan.fitjournal.ui.postworkout.export.ExportReason
import kz.maestrosultan.fitjournal.ui.postworkout.export.ExportRequest
import kz.maestrosultan.fitjournal.ui.postworkout.export.ExportResult
import kz.maestrosultan.fitjournal.ui.postworkout.format.MuscleTitleFormatter
import kz.maestrosultan.fitjournal.ui.postworkout.seams.ComposerDefaultsStore
import kz.maestrosultan.fitjournal.ui.postworkout.seams.PhotoPicker
import kz.maestrosultan.fitjournal.ui.postworkout.seams.PostWorkoutHaptics
import kz.maestrosultan.fitjournal.ui.postworkout.seams.SaveResult
import kz.maestrosultan.fitjournal.ui.postworkout.seams.SharePresenter

/**
 * Shared presentation for the share-card composer — the ONE ViewModel both
 * apps use. Pure state machine over [ComposerState]: defaults restore/persist
 * through [ComposerDefaultsStore], the pinned [ExportRequest]/[ExportResult]
 * handshake with the rendering composable, and dispatch of the finished PNG to
 * [SharePresenter] (share sheet vs photo library).
 *
 * ZERO domain writes by design: the composer renders a finished session's
 * summary — no repositories, no use cases, no sync triggers are injected.
 *
 * Close contract (the SINGLE close path): every dismiss gesture funnels into
 * [onCloseRequested], which persists the current setup and then emits [closed]
 * exactly once. Hosts dismiss ONLY by collecting [closed] — there is no
 * direct close callback anywhere else (see PostWorkoutCallbacks' note).
 *
 * Export handshake: [onShare]/[onSave] publish a fresh [ExportRequest] (ids
 * are a monotonically increasing counter); the composable renders + encodes
 * and answers via [onExportResult]. A result whose id doesn't match the
 * newest request is stale and dropped. Success routes by reason — Share
 * presents the share sheet, Save writes to the photo library — and only a
 * fully delivered export persists defaults (a share/save that failed leaves
 * the stored setup untouched). Failures surface as a [ComposerChip] that
 * auto-clears after [CHIP_AUTO_CLEAR]; the composer itself never blocks.
 */
class ShareComposerViewModel internal constructor(
    val summary: SessionSummary,
    val context: PostWorkoutContext,
    private val defaultsStore: ComposerDefaultsStore,
    private val photoPicker: PhotoPicker,
    private val sharePresenter: SharePresenter,
    private val haptics: PostWorkoutHaptics,
    muscleTitleFormatter: MuscleTitleFormatter,
) : ViewModel() {

    /**
     * Public construction path — the Android app module (a separate compilation
     * unit consuming :shared via Hilt) builds VMs itself, so class + constructor
     * must be public while [MuscleTitleFormatter] stays internal (its defaults
     * touch generated compose resources). Production always uses the Res-backed
     * formatter; jvmTest injects a deterministic one via the internal primary.
     */
    constructor(
        summary: SessionSummary,
        context: PostWorkoutContext,
        defaultsStore: ComposerDefaultsStore,
        photoPicker: PhotoPicker,
        sharePresenter: SharePresenter,
        haptics: PostWorkoutHaptics,
    ) : this(
        summary = summary,
        context = context,
        defaultsStore = defaultsStore,
        photoPicker = photoPicker,
        sharePresenter = sharePresenter,
        haptics = haptics,
        muscleTitleFormatter = MuscleTitleFormatter(),
    )

    private val _state = MutableStateFlow(ComposerState())
    val state: StateFlow<ComposerState> = _state.asStateFlow()

    // One-shot, single-consumer close event (buffered so emission never
    // depends on a collector being resumed at that exact moment).
    private val _closed = Channel<Unit>(Channel.BUFFERED)
    val closed: Flow<Unit> = _closed.receiveAsFlow()

    private var nextExportId = 0L
    private var closeRequested = false
    private var chipJob: Job? = null
    private var pickJob: Job? = null

    init {
        viewModelScope.launch {
            // The store contract says load() never throws, but a broken
            // platform store must degrade to first-run defaults, not crash.
            val saved = guarded { defaultsStore.load() }
                .onFailure { log("defaults load failed, using first-run defaults: $it") }
                .getOrNull()
            val title = muscleTitleFormatter.title(summary.muscles)
            _state.update { it.restoredFrom(saved).copy(title = title.take(ComposerState.MAX_TITLE_LENGTH)) }
        }
    }

    // ─── Card setup ─────────────────────────────────────────────────────

    fun onTitleChanged(title: String) {
        _state.update { it.copy(title = title.take(ComposerState.MAX_TITLE_LENGTH)) }
    }

    /** NewBest is only offered when the session actually set a PR; otherwise a no-op. */
    fun onLayoutSelected(layout: ShareLayoutKind) {
        if (layout == ShareLayoutKind.NewBest && summary.best == null) return
        if (layout == _state.value.layout) return
        _state.update { it.copy(layout = layout) }
        haptics.tick()
    }

    /** Photo routes through [onPickPhoto]; Brand/Transparent switch directly. */
    fun onBackdropSelected(kind: BackdropKind) {
        val backdrop = when (kind) {
            BackdropKind.Photo -> {
                onPickPhoto()
                return
            }
            BackdropKind.Brand -> ComposerBackdrop.Brand
            BackdropKind.Transparent -> ComposerBackdrop.Transparent
        }
        if (backdrop == _state.value.backdrop) return
        _state.update { it.copy(backdrop = backdrop) }
        haptics.tick()
    }

    /**
     * Launches the platform picker. Cancel (null) leaves the state untouched;
     * a throwing picker is treated the same — the user just stays where they
     * were. Re-picking while a photo backdrop is already set is allowed.
     */
    fun onPickPhoto() {
        if (pickJob?.isActive == true) return
        pickJob = viewModelScope.launch {
            val image = guarded { photoPicker.pickPhoto() }
                .onFailure { log("photo pick failed, treating as cancelled: $it") }
                .getOrNull() ?: return@launch
            _state.update { it.copy(backdrop = ComposerBackdrop.Photo(image)) }
            haptics.tick()
        }
    }

    fun onScrimChanged(scrim: Float) {
        _state.update { it.copy(scrim = scrim.coerceIn(0f, 1f)) }
    }

    /**
     * Keeps the exactly-[ComposerState.STATS_PICK_SIZE] invariant: selecting a
     * new stat replaces the OLDEST selection (the pick list is ordered
     * oldest-first); tapping an already-selected stat is a no-op — the Stats
     * layout always renders exactly three, so there is nothing to deselect to.
     */
    fun onStatToggled(stat: StatKind) {
        if (stat in _state.value.statsPick) return
        _state.update { current ->
            if (stat in current.statsPick) current
            else current.copy(statsPick = current.statsPick.drop(1) + stat)
        }
        haptics.tick()
    }

    fun onTransformChanged(transform: BlockTransform) {
        _state.update { it.copy(transform = transform) }
    }

    /** Removes the card block entirely — a photo-only card. Undone by [onResetLayout]. */
    fun onRemoveBlock() {
        _state.update { it.copy(blockRemoved = true) }
    }

    /** Back to the layout's natural placement: transform cleared, block restored. */
    fun onResetLayout() {
        _state.update { it.copy(transform = null, blockRemoved = false) }
    }

    fun onEditorSelected(editor: ComposerEditor?) {
        _state.update { it.copy(activeEditor = editor) }
    }

    // ─── Export handshake ───────────────────────────────────────────────

    fun onShare() = requestExport(ExportReason.Share)

    fun onSave() = requestExport(ExportReason.Save)

    private fun requestExport(reason: ExportReason) {
        val request = ExportRequest(id = ++nextExportId, reason = reason)
        _state.update { it.copy(exportRequest = request) }
    }

    /**
     * The composable's answer to [ComposerState.exportRequest]. Results for
     * anything but the newest request (superseded, replayed, or no request
     * pending) are stale and dropped.
     */
    fun onExportResult(result: ExportResult) {
        val pending = _state.value.exportRequest ?: return
        if (result.request.id != pending.id) return
        _state.update { it.copy(exportRequest = null) }
        when (result) {
            is ExportResult.Failure -> showChip(ComposerChip.ExportFailed)
            is ExportResult.Success -> viewModelScope.launch {
                deliver(result.request.reason, result.png)
            }
        }
    }

    private suspend fun deliver(reason: ExportReason, png: ByteArray) {
        when (reason) {
            ExportReason.Share -> {
                guarded { sharePresenter.presentShareSheet(png) }
                    .onSuccess { saveDefaults() }
                    .onFailure {
                        log("share sheet presentation failed: $it")
                        showChip(ComposerChip.ExportFailed)
                    }
            }
            ExportReason.Save -> {
                val outcome = guarded { sharePresenter.saveToPhotos(png) }
                    .onFailure { log("photo-library save threw: $it") }
                    .getOrDefault(SaveResult.Failed)
                when (outcome) {
                    SaveResult.Saved -> {
                        haptics.success()
                        saveDefaults()
                    }
                    SaveResult.PermissionDenied -> showChip(ComposerChip.SavePermission)
                    SaveResult.Failed -> showChip(ComposerChip.SaveFailed)
                }
            }
        }
    }

    // ─── Close contract ─────────────────────────────────────────────────

    /**
     * THE single close path: persists the current setup, then emits [closed]
     * exactly once. Idempotent — a second call (double-tap, gesture + button)
     * neither re-saves nor re-emits.
     */
    fun onCloseRequested() {
        if (closeRequested) return
        closeRequested = true
        viewModelScope.launch {
            saveDefaults()
            _closed.send(Unit)
        }
    }

    // ─── Internals ──────────────────────────────────────────────────────

    /** Best-effort: a failing store must never block a share, save, or close. */
    private suspend fun saveDefaults() {
        val s = _state.value
        val defaults = ComposerDefaults(
            layout = s.layout,
            // A Photo backdrop persists AS Photo kind (the bitmap itself is
            // not persisted; restore degrades it to Brand).
            backdropKind = s.backdrop.kind,
            statsPick = s.statsPick,
            scrim = s.scrim,
            transform = s.transform,
            blockRemoved = s.blockRemoved,
        )
        guarded { defaultsStore.save(defaults) }
            .onFailure { log("defaults save failed (ignored): $it") }
    }

    private fun showChip(chip: ComposerChip) {
        chipJob?.cancel()
        _state.update { it.copy(chip = chip) }
        chipJob = viewModelScope.launch {
            delay(CHIP_AUTO_CLEAR)
            _state.update { it.copy(chip = null) }
        }
    }

    private fun ComposerState.restoredFrom(saved: ComposerDefaults?): ComposerState {
        if (saved == null) return this
        return copy(
            // NewBest only renders with a PR; a stale saved preference falls back.
            layout = when {
                saved.layout == ShareLayoutKind.NewBest && summary.best == null -> ShareLayoutKind.Stats
                else -> saved.layout
            },
            // Photo kind restores as Brand — the bitmap was never persisted.
            backdrop = when (saved.backdropKind) {
                BackdropKind.Photo, BackdropKind.Brand -> ComposerBackdrop.Brand
                BackdropKind.Transparent -> ComposerBackdrop.Transparent
            },
            // Defend the exactly-3 invariant against hand-edited/legacy stores.
            statsPick = saved.statsPick.distinct()
                .takeIf { it.size == ComposerState.STATS_PICK_SIZE }
                ?: statsPick,
            scrim = saved.scrim.coerceIn(0f, 1f),
            transform = saved.transform,
            blockRemoved = saved.blockRemoved,
        )
    }

    private fun log(message: String) {
        println("[FJ_COMPOSER] $message")
    }

    /**
     * Cancel the scope. Mirrors WorkoutViewModel: this VM is host-owned (the
     * platform sheet/controller drives it), so it is NOT in a ViewModelStore
     * that would call `clear()` — the host calls this on teardown.
     */
    fun dispose() {
        viewModelScope.cancel()
    }

    private companion object {
        val CHIP_AUTO_CLEAR: Duration = 2.seconds
    }
}

/** [runCatching] that rethrows cancellation — seam calls may fail; the composer degrades, never crashes. */
private inline fun <T> guarded(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
