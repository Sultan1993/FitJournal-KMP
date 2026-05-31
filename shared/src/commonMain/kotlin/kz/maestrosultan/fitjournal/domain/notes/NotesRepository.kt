package kz.maestrosultan.fitjournal.domain.notes

import kotlinx.coroutines.flow.Flow
import kz.maestrosultan.fitjournal.domain.notes.Note

interface NotesRepository {

    // ─── Reads ─────────────────────────────────────────────────────────

    /** All live notes for [userId], sorted newest-first by `createdDate`. */
    suspend fun getNotes(userId: String): List<Note>
    fun getNotesFlow(userId: String): Flow<List<Note>>

    /** Live pinned notes for [userId], sorted by `updatedDate DESC`. */
    suspend fun getPinnedNotes(userId: String): List<Note>
    fun getPinnedNotesFlow(userId: String): Flow<List<Note>>

    /**
     * Returns null when the note doesn't exist OR has been soft-deleted
     * (a tombstone from another device looks like "not found" to the UI).
     */
    suspend fun getNoteById(uuid: String): Note?

    // ─── Writes ────────────────────────────────────────────────────────

    suspend fun createNote(uuid: String, userId: String, text: String, isPinned: Boolean)

    suspend fun updateNote(uuid: String, text: String, isPinned: Boolean)

    /**
     * Soft delete — tombstones the row and flips `pendingUpload=1` so
     * the SyncOrchestrator pushes the deletion to AWS on its next tick.
     * Matches the convention used elsewhere in the app (workouts,
     * journals, etc.). Hard purge is reserved for [deleteUserNotes].
     */
    suspend fun deleteNote(uuid: String)

    /** Hard purge: used by Settings → delete account. */
    suspend fun deleteUserNotes(userId: String)
}
