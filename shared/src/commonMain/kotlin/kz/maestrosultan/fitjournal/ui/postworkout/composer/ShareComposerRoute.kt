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
 * Dismissal is NOT a parameter here: hosts collect [ShareComposerViewModel.closed]
 * and pop their own navigation. The composer never calls back to close, so
 * there is exactly one close path however it was triggered — chip, system Back,
 * or a completed share.
 */
@Composable
fun ShareComposerRoute(
    viewModel: ShareComposerViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary = viewModel.summary

    ShareComposerScreen(
        state = state,
        hasPersonalRecord = summary.best != null,
        onCloseRequested = viewModel::onCloseRequested,
        onEditorSelected = viewModel::onEditorSelected,
        onTitleChanged = viewModel::onTitleChanged,
        onLayoutSelected = viewModel::onLayoutSelected,
        onResetLayout = viewModel::onResetLayout,
        onBackdropSelected = viewModel::onBackdropSelected,
        onPickPhoto = viewModel::onPickPhoto,
        onStatToggled = viewModel::onStatToggled,
        onScrimChanged = viewModel::onScrimChanged,
        onShare = viewModel::onShare,
        onSave = viewModel::onSave,
        onExportResult = viewModel::onExportResult,
        modifier = modifier,
    ) {
        val data = shareCardData(
            summary = summary,
            title = state.title,
            statsPick = state.statsPick,
            units = viewModel.context.units,
        )
        Box(Modifier.fillMaxSize()) {
            ShareCardBody(
                layout = state.layout,
                data = data,
                transform = state.transform,
                blockRemoved = state.blockRemoved,
                haptics = viewModel.haptics,
                onTransformChanged = viewModel::onTransformChanged,
                onRemoveBlock = viewModel::onRemoveBlock,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
