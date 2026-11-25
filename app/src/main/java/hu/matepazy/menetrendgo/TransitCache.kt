package hu.matepazy.menetrendgo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The app-wide answer cache: per-key freshness, an LRU bound, and single-flight loading, so the map,
 * the widget and a drawer asking for the same thing at once cause one request. */
class TransitCache(private val maxEntries: Int = 256, private val clock: () -> Long = System::currentTimeMillis) {
    private class Entry(val value: Any?, val at: Long)
    private val lock = Mutex()
    private val entries = LinkedHashMap<String, Entry>(32, .75f, true)
    private val inflight = HashMap<String, CompletableDeferred<Any?>>()

    /** A value younger than [maxAgeMillis] is reused unless [force]; otherwise [load] runs once for every
     * concurrent caller. When it fails, a value younger than [staleMillis] stands in for it. Cancelling the
     * loading caller does not fail the others: they retry. */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> get(key: String, maxAgeMillis: Long, force: Boolean = false, staleMillis: Long = 0, load: suspend () -> T): T {
        while (true) {
            val (deferred, owner) = lock.withLock {
                if (!force) entries[key]?.takeIf { clock() - it.at < maxAgeMillis }?.let { return it.value as T }
                inflight[key]?.let { it to false } ?: (CompletableDeferred<Any?>().also { inflight[key] = it } to true)
            }
            if (!owner) {
                try { return deferred.await() as T }
                catch (e: CancellationException) { currentCoroutineContext().ensureActive(); continue }
            }
            try {
                val value = load()
                lock.withLock {
                    entries[key] = Entry(value, clock())
                    while (entries.size > maxEntries) entries.remove(entries.keys.first())
                    inflight.remove(key)
                }
                deferred.complete(value)
                return value
            } catch (e: Throwable) {
                val stale = lock.withLock {
                    inflight.remove(key)
                    if (e is CancellationException || staleMillis <= 0) null else entries[key]?.takeIf { clock() - it.at <= staleMillis }
                }
                if (stale != null) { deferred.complete(stale.value); return stale.value as T }
                deferred.completeExceptionally(e)
                throw e
            }
        }
    }

    suspend fun clear() = lock.withLock { entries.clear() }
}
