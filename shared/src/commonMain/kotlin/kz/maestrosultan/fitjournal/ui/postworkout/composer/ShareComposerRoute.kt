package kz.maestrosultan.fitjournal.ui.postworkout.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The share composer, bound to its ViewModel — the single entry point both apps
 * host.
 *
 * Everything the card is made of ([ShareCardScope], the four layouts, the
 * freeform block) stays module-internal behind this one public function. The
 * alternative — widening that surface so each app module could assemble the
 * card slot itself — would have put the same twenty lines of composition in two
 * places, in two languages, and made every future card change a three-repo
 * edit. Hosts supply a ViewModel and a modifier; nothing else.
 *
 * Dismissal is NOT a parameter here: hosts collect [ShareComposerViewModel.viewEffect]
 * ([ShareComposerContract.ViewEffect.Closed]) and pop their own navigation. The
 * composer never calls back to close, so
 * there is exactly one close path however it was triggered — chip, system Back,
 * or a completed share.
 */
@Composable
fun ShareComposerRoute(
    viewModel: ShareComposerViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val summary = viewModel.summary

    // Resolved ONCE, here, and handed to both compositions as a plain value.
    // `shareCardData` resolves ~15 string resources asynchronously; the export
    // instance is a SEPARATE composition captured after two frames, so resolving
    // inside the card slot instead could land the two on different frames — a
    // PNG with blank stat labels beside a preview that reads correctly.
    val data = shareCardData(
        summary = summary,
        // A cleared or whitespace-only title falls back to the muscle-derived
        // one rather than exporting a blank headline.
        title = state.title.ifBlank { viewModel.defaultTitle },
        statsPick = state.statsPick,
        units = viewModel.context.units,
    )

    ShareComposerScreen(
        state = state,
        hasPersonalRecord = summary.best != null,
        onCloseRequested = { viewModel.dispatch(ShareComposerContract.ViewAction.CloseRequested) },
        onEditorSelected = { viewModel.dispatch(ShareComposerContract.ViewAction.EditorSelected(it)) },
        onTitleChanged = { viewModel.dispatch(ShareComposerContract.ViewAction.TitleChanged(it)) },
        onLayoutSelected = { viewModel.dispatch(ShareComposerContract.ViewAction.LayoutSelected(it)) },
        onResetLayout = { viewModel.dispatch(ShareComposerContract.ViewAction.ResetLayout) },
        onBackdropSelected = { viewModel.dispatch(ShareComposerContract.ViewAction.BackdropSelected(it)) },
        onPickPhoto = { viewModel.dispatch(ShareComposerContract.ViewAction.PickPhoto) },
        onStatToggled = { viewModel.dispatch(ShareComposerContract.ViewAction.StatToggled(it)) },
        onScrimChanged = { viewModel.dispatch(ShareComposerContract.ViewAction.ScrimChanged(it)) },
        onShare = { viewModel.dispatch(ShareComposerContract.ViewAction.Share) },
        onSave = { viewModel.dispatch(ShareComposerContract.ViewAction.Save) },
        onExportResult = { viewModel.dispatch(ShareComposerContract.ViewAction.ExportResult(it)) },
        modifier = modifier,
    ) { exportMode ->
        Box(Modifier.fillMaxSize()) {
            ShareCardBody(
                layout = state.layout,
                data = data,
                transform = state.transform,
                blockRemoved = state.blockRemoved,
                haptics = viewModel.haptics,
                onTransformChanged = { viewModel.dispatch(ShareComposerContract.ViewAction.TransformChanged(it)) },
                onRemoveBlock = { viewModel.dispatch(ShareComposerContract.ViewAction.RemoveBlock) },
                modifier = Modifier.fillMaxSize(),
                exportMode = exportMode,
            )
        }
    }
}
