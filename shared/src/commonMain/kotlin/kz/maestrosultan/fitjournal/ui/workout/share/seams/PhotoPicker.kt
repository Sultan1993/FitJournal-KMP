package kz.maestrosultan.fitjournal.ui.workout.share.seams

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Platform photo picking for the composer's Photo backdrop.
 *
 * An interface (not expect/actual) because implementations own platform UI
 * lifecycle (PHPicker / Photo Picker activity contract) and the ViewModel
 * needs a fake in tests.
 *
 * Suspends for the whole pick interaction; returns null when the user cancels
 * or the picked asset cannot be decoded.
 */
interface PhotoPicker {
    suspend fun pickPhoto(): ImageBitmap?
}
