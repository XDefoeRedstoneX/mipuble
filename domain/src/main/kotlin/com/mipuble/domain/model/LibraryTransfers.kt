package com.mipuble.domain.model

/** Outcome of importing one book; lets callers report duplicates as skipped. */
sealed interface ImportOutcome {
    data class Added(val bookId: Long) : ImportOutcome
    data object Duplicate : ImportOutcome
}

/** Tally of a batch upload: how many were sent vs. skipped as already present. */
data class UploadSummary(val added: Int, val skipped: Int) {
    val total: Int get() = added + skipped
}
