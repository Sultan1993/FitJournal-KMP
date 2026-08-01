package kz.maestrosultan.fitjournal.ui.postworkout.export

/** Why an export was requested — decides where the PNG goes afterwards. */
enum class ExportReason { Share, Save }

/**
 * One card-export request. [id] is a monotonically increasing token the
 * ViewModel uses to correlate the async render result with the request that
 * started it; a result whose id no longer matches the newest request is stale
 * and gets dropped.
 */
data class ExportRequest(val id: Long, val reason: ExportReason)

/** Outcome of rendering + PNG-encoding a share card for an [ExportRequest]. */
sealed interface ExportResult {

    data class Success(val request: ExportRequest, val png: ByteArray) : ExportResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return request == other.request && png.contentEquals(other.png)
        }

        override fun hashCode(): Int = 31 * request.hashCode() + png.contentHashCode()
    }

    data class Failure(val request: ExportRequest) : ExportResult
}
