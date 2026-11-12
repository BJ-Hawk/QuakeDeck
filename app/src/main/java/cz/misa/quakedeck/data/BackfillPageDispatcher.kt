package cz.misa.quakedeck.data

import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

/** The download worker waits for one page; main-thread turns apply at most one report. */
internal class BackfillPageDispatcher<T>(
    private val post: (() -> Unit) -> Unit,
    private val isCurrent: () -> Boolean,
    private val apply: (T) -> Unit
) {
    fun dispatch(page: List<T>): CompletableFuture<Unit> {
        val result = CompletableFuture<Unit>()
        var index = 0
        fun step() {
            try {
                if (!isCurrent()) throw CancellationException("Historical download cancelled")
                if (index < page.size) apply(page[index++])
                if (index < page.size) post(::step) else result.complete(Unit)
            } catch (error: Exception) {
                result.completeExceptionally(error)
            }
        }
        post(::step)
        return result
    }
}
