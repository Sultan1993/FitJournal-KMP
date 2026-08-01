package kz.maestrosultan.fitjournal.ui.postworkout.composer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.summary.MuscleLoad
import kz.maestrosultan.fitjournal.domain.workout.summary.SessionBest
import kz.maestrosultan.fitjournal.domain.workout.summary.SessionSummary
import kz.maestrosultan.fitjournal.ui.postworkout.PostWorkoutContext
import kz.maestrosultan.fitjournal.ui.postworkout.export.ExportReason
import kz.maestrosultan.fitjournal.ui.postworkout.export.ExportResult
import kz.maestrosultan.fitjournal.ui.postworkout.format.MuscleTitleFormatter
import kz.maestrosultan.fitjournal.ui.postworkout.seams.ComposerDefaultsStore
import kz.maestrosultan.fitjournal.ui.postworkout.seams.PhotoPicker
import kz.maestrosultan.fitjournal.ui.postworkout.seams.PostWorkoutHaptics
import kz.maestrosultan.fitjournal.ui.postworkout.seams.SaveResult
import kz.maestrosultan.fitjournal.ui.postworkout.seams.SharePresenter

/**
 * Failure-contract suite for [ShareComposerViewModel]: defaults restore/save
 * through the store seam, the exactly-3 stats-pick invariant, the pinned
 * ExportRequest/ExportResult handshake (Share vs Save dispatch and every
 * failure chip), the photo-pick seam, and the SINGLE close path
 * (onCloseRequested → save defaults → one `closed` event).
 *
 * All seams are fakes; the [MuscleTitleFormatter] gets deterministic name
 * lambdas so no compose resource loading happens on the JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShareComposerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val store = FakeDefaultsStore()
    private val picker = FakePhotoPicker()
    private val presenter = FakeSharePresenter()
    private val haptics = FakeHaptics()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(summary: SessionSummary = summary()): ShareComposerViewModel =
        ShareComposerViewModel(
            summary = summary,
            context = PostWorkoutContext(
                userId = "user-1",
                journalId = "journal-1",
                date = LocalDate(2026, 7, 31),
                workoutNumber = 1,
                units = MeasurementSystem.KG_KM,
            ),
            defaultsStore = store,
            photoPicker = picker,
            sharePresenter = presenter,
            haptics = haptics,
            muscleTitleFormatter = MuscleTitleFormatter(
                categoryName = { it.identifier },
                fallbackTitle = { "Workout" },
            ),
        )

    // ─── Restore on init ────────────────────────────────────────────────

    @Test
    fun init_emptyStore_appliesFirstRunDefaults_andComposesTitle() = runTest {
        val vm = createVm()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(ShareLayoutKind.Stats, state.layout)
        assertEquals(ComposerBackdrop.Brand, state.backdrop)
        assertEquals(1.0f, state.scrim)
        assertEquals(listOf(StatKind.Duration, StatKind.Sets, StatKind.BestSet), state.statsPick)
        assertNull(state.transform)
        assertFalse(state.blockRemoved)
        assertNull(state.exportRequest)
        assertNull(state.chip)
        assertEquals("shoulders · chest · triceps", state.title)
    }

    @Test
    fun init_loadThrowing_fallsBackToFirstRunDefaults() = runTest {
        store.loadError = IllegalStateException("corrupt store")

        val vm = createVm()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(ShareLayoutKind.Stats, state.layout)
        assertEquals(ComposerBackdrop.Brand, state.backdrop)
        assertEquals(1.0f, state.scrim)
        assertEquals(listOf(StatKind.Duration, StatKind.Sets, StatKind.BestSet), state.statsPick)
        assertEquals("shoulders · chest · triceps", state.title)
    }

    @Test
    fun init_savedDefaults_areApplied() = runTest {
        val transform = BlockTransform(cx = 0.4f, cy = 0.3f, scale = 1.2f, rotationDeg = 15f)
        store.stored = ComposerDefaults(
            layout = ShareLayoutKind.Receipt,
            backdropKind = BackdropKind.Transparent,
            statsPick = listOf(StatKind.Exercises, StatKind.TotalReps, StatKind.Duration),
            scrim = 0.45f,
            transform = transform,
            blockRemoved = true,
        )

        val vm = createVm()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(ShareLayoutKind.Receipt, state.layout)
        assertEquals(ComposerBackdrop.Transparent, state.backdrop)
        assertEquals(0.45f, state.scrim)
        assertEquals(listOf(StatKind.Exercises, StatKind.TotalReps, StatKind.Duration), state.statsPick)
        assertEquals(transform, state.transform)
        assertTrue(state.blockRemoved)
    }

    @Test
    fun init_photoBackdropKind_restoresAsBrand() = runTest {
        store.stored = defaults(backdropKind = BackdropKind.Photo)

        val vm = createVm()
        advanceUntilIdle()

        assertEquals(ComposerBackdrop.Brand, vm.state.value.backdrop)
    }

    @Test
    fun init_newBestLayout_withoutPr_restoresAsStats() = runTest {
        store.stored = defaults(layout = ShareLayoutKind.NewBest)

        val vm = createVm(summary = summary(best = null))
        advanceUntilIdle()

        assertEquals(ShareLayoutKind.Stats, vm.state.value.layout)
    }

    @Test
    fun init_newBestLayout_withPr_staysNewBest() = runTest {
        store.stored = defaults(layout = ShareLayoutKind.NewBest)

        val vm = createVm(summary = summary(best = sessionBest()))
        advanceUntilIdle()

        assertEquals(ShareLayoutKind.NewBest, vm.state.value.layout)
    }

    // ─── Stats pick ─────────────────────────────────────────────────────

    @Test
    fun statsPick_selectingFourth_replacesOldestSelection() = runTest {
        val vm = createVm()
        advanceUntilIdle()

        vm.onStatToggled(StatKind.Exercises)

        assertEquals(listOf(StatKind.Sets, StatKind.BestSet, StatKind.Exercises), vm.state.value.statsPick)
        assertEquals(1, haptics.tickCount)
    }

    @Test
    fun statsPick_tappingSelectedChip_atExactlyThree_isNoOp() = runTest {
        val vm = createVm()
        advanceUntilIdle()

        vm.onStatToggled(StatKind.Sets)

        assertEquals(listOf(StatKind.Duration, StatKind.Sets, StatKind.BestSet), vm.state.value.statsPick)
        assertEquals(0, haptics.tickCount)
    }

    @Test
    fun statsPick_staysAtExactlyThree_acrossAnyToggleSequence() = runTest {
        val vm = createVm()
        advanceUntilIdle()

        vm.onStatToggled(StatKind.Exercises)
        vm.onStatToggled(StatKind.TotalReps)
        vm.onStatToggled(StatKind.Duration)
        vm.onStatToggled(StatKind.Duration) // now selected again → no-op

        val pick = vm.state.value.statsPick
        assertEquals(3, pick.size)
        assertEquals(pick.distinct(), pick)
        assertEquals(listOf(StatKind.Exercises, StatKind.TotalReps, StatKind.Duration), pick)
    }

    // ─── Layout ─────────────────────────────────────────────────────────

    @Test
    fun layout_newBest_isNotSelectable_whenSummaryHasNoBest() = runTest {
        val vm = createVm(summary = summary(best = null))
        advanceUntilIdle()

        vm.onLayoutSelected(ShareLayoutKind.NewBest)

        assertEquals(ShareLayoutKind.Stats, vm.state.value.layout)
    }

    @Test
    fun layout_newBest_isSelectable_whenSummaryHasBest() = runTest {
        val vm = createVm(summary = summary(best = sessionBest()))
        advanceUntilIdle()

        vm.onLayoutSelected(ShareLayoutKind.NewBest)

        assertEquals(ShareLayoutKind.NewBest, vm.state.value.layout)
    }

    // ─── Block transform / reset ────────────────────────────────────────

    @Test
    fun resetLayout_clearsTransform_andBlockRemoved() = runTest {
        val vm = createVm()
        advanceUntilIdle()

        vm.onTransformChanged(BlockTransform(cx = 0.7f, cy = 0.2f, scale = 0.8f, rotationDeg = -30f))
        vm.onRemoveBlock()
        assertNotNull(vm.state.value.transform)
        assertTrue(vm.state.value.blockRemoved)

        vm.onResetLayout()

        assertNull(vm.state.value.transform)
        assertFalse(vm.state.value.blockRemoved)
    }

    // ─── Title ──────────────────────────────────────────────────────────

    @Test
    fun titleEdit_clampsToSixtyChars() = runTest {
        val vm = createVm()
        advanceUntilIdle()

        vm.onTitleChanged("a".repeat(61))
        assertEquals("a".repeat(60), vm.state.value.title)

        vm.onTitleChanged("b".repeat(60))
        assertEquals("b".repeat(60), vm.state.value.title)
    }

    // ─── Export handshake ───────────────────────────────────────────────

    @Test
    fun onShare_setsExportRequest_withShareReason_andUniqueIds() = runTest {
        val vm = createVm()
        advanceUntilIdle()

        vm.onShare()
        val first = assertNotNull(vm.state.value.exportRequest)
        assertEquals(ExportReason.Share, first.reason)

        vm.onShare()
        val second = assertNotNull(vm.state.value.exportRequest)
        assertNotEquals(first.id, second.id)
        assertTrue(second.id > first.id)
    }

    @Test
    fun onSave_setsExportRequest_withSaveReason() = runTest {
        val vm = createVm()
        advanceUntilIdle()

        vm.onSave()

        assertEquals(ExportReason.Save, assertNotNull(vm.state.value.exportRequest).reason)
    }

    @Test
    fun exportSuccess_share_presentsShareSheet_clearsRequest_savesDefaults() = runTest {
        val vm = createVm()
        advanceUntilIdle()

        vm.onShare()
        val request = assertNotNull(vm.state.value.exportRequest)
        val result = ExportResult.Success(request, PNG)
        vm.onExportResult(result)
        assertNull(vm.state.value.exportRequest)
        advanceUntilIdle()

        assertEquals(1, presenter.shareCalls.size)
        assertTrue(presenter.shareCalls.single().contentEquals(PNG))
        assertEquals(1, store.saved.size)
        assertNull(vm.state.value.chip)

        // Replaying the same (now unmatched) result is dropped.
        vm.onExportResult(result)
        advanceUntilIdle()
        assertEquals(1, presenter.shareCalls.size)
    }

    @Test
    fun exportSuccess_save_savesToPhotos_firesSuccessHaptic_savesDefaults() = runTest {
        presenter.saveResult = SaveResult.Saved
        val vm = createVm()
        advanceUntilIdle()

        vm.onSave()
        val request = assertNotNull(vm.state.value.exportRequest)
        vm.onExportResult(ExportResult.Success(request, PNG))
        advanceUntilIdle()

        assertEquals(1, presenter.saveCalls.size)
        assertTrue(presenter.saveCalls.single().contentEquals(PNG))
        assertEquals(0, presenter.shareCalls.size)
        assertEquals(1, haptics.successCount)
        assertEquals(1, store.saved.size)
        assertNull(vm.state.value.exportRequest)
        assertNull(vm.state.value.chip)
    }

    @Test
    fun shareSheetThrowing_showsExportChip_clearsRequest_doesNotSaveDefaults() = runTest {
        presenter.shareError = RuntimeException("no presenting VC")
        val vm = createVm()
        advanceUntilIdle()

        vm.onShare()
        val request = assertNotNull(vm.state.value.exportRequest)
        vm.onExportResult(ExportResult.Success(request, PNG))
        runCurrent()

        assertEquals(ComposerChip.ExportFailed, vm.state.value.chip)
        assertNull(vm.state.value.exportRequest)
        assertTrue(store.saved.isEmpty())
        assertEquals(0, haptics.successCount)
    }

    @Test
    fun saveFailed_showsSaveFailedChip_andDoesNotSaveDefaults() = runTest {
        presenter.saveResult = SaveResult.Failed
        val vm = createVm()
        advanceUntilIdle()

        vm.onSave()
        val request = assertNotNull(vm.state.value.exportRequest)
        vm.onExportResult(ExportResult.Success(request, PNG))
        runCurrent()

        assertEquals(ComposerChip.SaveFailed, vm.state.value.chip)
        assertNull(vm.state.value.exportRequest)
        assertTrue(store.saved.isEmpty())
        assertEquals(0, haptics.successCount)
    }

    @Test
    fun savePermissionDenied_showsPermissionChip() = runTest {
        presenter.saveResult = SaveResult.PermissionDenied
        val vm = createVm()
        advanceUntilIdle()

        vm.onSave()
        val request = assertNotNull(vm.state.value.exportRequest)
        vm.onExportResult(ExportResult.Success(request, PNG))
        runCurrent()

        assertEquals(ComposerChip.SavePermission, vm.state.value.chip)
        assertEquals(0, haptics.successCount)
    }

    @Test
    fun exportFailure_showsExportChip_clearsRequest_neverTouchesPresenter() = runTest {
        val vm = createVm()
        advanceUntilIdle()

        vm.onShare()
        val request = assertNotNull(vm.state.value.exportRequest)
        vm.onExportResult(ExportResult.Failure(request))
        runCurrent()

        assertEquals(ComposerChip.ExportFailed, vm.state.value.chip)
        assertNull(vm.state.value.exportRequest)
        assertEquals(0, presenter.shareCalls.size)
        assertEquals(0, presenter.saveCalls.size)
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun staleExportResult_isDropped() = runTest {
        val vm = createVm()
        advanceUntilIdle()

        vm.onShare()
        val stale = assertNotNull(vm.state.value.exportRequest)
        vm.onShare()
        val newest = assertNotNull(vm.state.value.exportRequest)

        vm.onExportResult(ExportResult.Success(stale, PNG))
        advanceUntilIdle()

        assertEquals(0, presenter.shareCalls.size)
        assertEquals(newest, vm.state.value.exportRequest)
    }

    @Test
    fun chip_autoClears_afterTwoSeconds() = runTest {
        val vm = createVm()
        advanceUntilIdle()

        vm.onShare()
        vm.onExportResult(ExportResult.Failure(assertNotNull(vm.state.value.exportRequest)))
        runCurrent()
        assertEquals(ComposerChip.ExportFailed, vm.state.value.chip)

        advanceTimeBy(2_001)

        assertNull(vm.state.value.chip)
    }

    // ─── Photo pick ─────────────────────────────────────────────────────

    @Test
    fun pickPhoto_success_setsPhotoBackdrop() = runTest {
        picker.result = FakeBitmap
        val vm = createVm()
        advanceUntilIdle()

        vm.onPickPhoto()
        advanceUntilIdle()

        val backdrop = assertIs<ComposerBackdrop.Photo>(vm.state.value.backdrop)
        assertSame(FakeBitmap, backdrop.image)
    }

    @Test
    fun pickPhoto_cancelled_leavesStateUnchanged() = runTest {
        picker.result = null
        val vm = createVm()
        advanceUntilIdle()
        val before = vm.state.value

        vm.onPickPhoto()
        advanceUntilIdle()

        assertEquals(before, vm.state.value)
    }

    @Test
    fun pickPhoto_throwing_isTreatedAsCancelled() = runTest {
        picker.error = IllegalStateException("picker exploded")
        val vm = createVm()
        advanceUntilIdle()
        val before = vm.state.value

        vm.onPickPhoto()
        advanceUntilIdle()

        assertEquals(before, vm.state.value)
    }

    // ─── Defaults save / close contract ─────────────────────────────────

    @Test
    fun close_savesCurrentDefaults_thenEmitsClosedOnce() = runTest {
        val vm = createVm()
        advanceUntilIdle()

        vm.onLayoutSelected(ShareLayoutKind.Receipt)
        vm.onCloseRequested()
        advanceUntilIdle()

        assertEquals(1, drainClosedEvents(vm))
        assertEquals(1, store.saved.size)
        assertEquals(ShareLayoutKind.Receipt, store.saved.single().layout)
    }

    @Test
    fun close_doubleInvoke_stillEmitsOneEvent_andSavesOnce() = runTest {
        val vm = createVm()
        advanceUntilIdle()

        vm.onCloseRequested()
        vm.onCloseRequested()
        advanceUntilIdle()

        assertEquals(1, drainClosedEvents(vm))
        assertEquals(1, store.saved.size)
    }

    @Test
    fun close_storeSaveThrowing_isSwallowed_closeStillEmits() = runTest {
        store.saveError = IllegalStateException("disk full")
        val vm = createVm()
        advanceUntilIdle()

        vm.onCloseRequested()
        advanceUntilIdle()

        assertEquals(1, drainClosedEvents(vm))
    }

    @Test
    fun close_persistsPhotoBackdrop_asPhotoKind() = runTest {
        picker.result = FakeBitmap
        val vm = createVm()
        advanceUntilIdle()

        vm.onPickPhoto()
        advanceUntilIdle()
        vm.onCloseRequested()
        advanceUntilIdle()

        assertEquals(BackdropKind.Photo, store.saved.single().backdropKind)
    }

    // ─── Purity ─────────────────────────────────────────────────────────

    @Test
    fun constructor_injectsNoRepositoryOrSyncTypes() {
        val parameterTypeNames = ShareComposerViewModel::class.java.constructors
            .flatMap { it.parameterTypes.asList() }
            .map { it.name }

        assertTrue(parameterTypeNames.isNotEmpty())
        parameterTypeNames.forEach { name ->
            assertFalse(name.contains("Repository"), "composer must not write domain data: $name")
            assertFalse(name.contains("SyncTrigger"), "composer must not trigger sync: $name")
            assertFalse(name.contains("UseCase"), "composer must not invoke use cases: $name")
        }
    }

    // ─── Fixtures ───────────────────────────────────────────────────────

    /**
     * Drains everything buffered in the `closed` channel and returns the count.
     * Collected AFTER the fact via a foreground collector + explicit cancel —
     * a backgroundScope collector is not reliably resumed by advanceUntilIdle
     * once the foreground goes idle, and the channel buffers anyway.
     */
    private fun TestScope.drainClosedEvents(vm: ShareComposerViewModel): Int {
        var count = 0
        val collector = launch { vm.closed.collect { count++ } }
        advanceUntilIdle()
        collector.cancel()
        return count
    }

    private fun summary(best: SessionBest? = null): SessionSummary = SessionSummary(
        session = WorkoutSession(
            id = "session-1",
            userId = "user-1",
            journalId = "journal-1",
            date = LocalDate(2026, 7, 31),
            workoutNumber = 1,
            startedAt = Instant.fromEpochMilliseconds(0),
            endedAt = Instant.fromEpochMilliseconds(3_600_000),
        ),
        muscles = listOf(
            MuscleLoad(category = CategoryType.SHOULDERS, loggedSets = 9),
            MuscleLoad(category = CategoryType.CHEST, loggedSets = 5),
            MuscleLoad(category = CategoryType.TRICEPS, loggedSets = 3),
        ),
        exercises = emptyList(),
        tonnageKg = 1_000.0,
        loggedSets = 17,
        exerciseCount = 4,
        weekOrdinal = 2,
        best = best,
        sessionRecordUuids = emptySet(),
    )

    private fun sessionBest(): SessionBest = SessionBest(
        exerciseName = "Bench Press",
        weightKg = 100.0,
        reps = 8,
        previousBestKg = 95.0,
        previousBestDate = LocalDate(2026, 7, 1),
    )

    private fun defaults(
        layout: ShareLayoutKind = ShareLayoutKind.Stats,
        backdropKind: BackdropKind = BackdropKind.Brand,
    ): ComposerDefaults = ComposerDefaults(
        layout = layout,
        backdropKind = backdropKind,
        statsPick = listOf(StatKind.Duration, StatKind.Sets, StatKind.BestSet),
        scrim = 1.0f,
        transform = null,
        blockRemoved = false,
    )

    private companion object {
        val PNG = byteArrayOf(0x50, 0x4E, 0x47)
    }
}

// ─── Fakes ──────────────────────────────────────────────────────────────

private class FakeDefaultsStore : ComposerDefaultsStore {
    var stored: ComposerDefaults? = null
    var loadError: Throwable? = null
    var saveError: Throwable? = null
    val saved = mutableListOf<ComposerDefaults>()

    override suspend fun load(): ComposerDefaults? {
        loadError?.let { throw it }
        return stored
    }

    override suspend fun save(defaults: ComposerDefaults) {
        saveError?.let { throw it }
        saved += defaults
    }
}

private class FakePhotoPicker : PhotoPicker {
    var result: ImageBitmap? = null
    var error: Throwable? = null
    var pickCount = 0

    override suspend fun pickPhoto(): ImageBitmap? {
        pickCount++
        error?.let { throw it }
        return result
    }
}

private class FakeSharePresenter : SharePresenter {
    var shareError: Throwable? = null
    var saveResult: SaveResult = SaveResult.Saved
    val shareCalls = mutableListOf<ByteArray>()
    val saveCalls = mutableListOf<ByteArray>()

    override suspend fun presentShareSheet(png: ByteArray) {
        shareError?.let { throw it }
        shareCalls += png
    }

    override suspend fun saveToPhotos(png: ByteArray): SaveResult {
        saveCalls += png
        return saveResult
    }
}

private class FakeHaptics : PostWorkoutHaptics {
    var tickCount = 0
    var successCount = 0

    override fun tick() {
        tickCount++
    }

    override fun success() {
        successCount++
    }
}

/** Pure-JVM [ImageBitmap]; no Skia/Skiko needed just to carry a backdrop reference. */
private object FakeBitmap : ImageBitmap {
    override val width: Int = 1
    override val height: Int = 1
    override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888
    override val hasAlpha: Boolean = true
    override val colorSpace: ColorSpace = ColorSpaces.Srgb

    override fun prepareToDraw() = Unit

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int,
    ) = Unit
}
