package org.jellyfin.androidtv.ui.home

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.InvalidContentException

class HomeSectionLoaderTests {
    @Test
    fun `a failed shelf does not hide successful shelves or wait for a slow shelf`() = runBlocking {
        val slow = CompletableDeferred<Unit>()
        val libraryLoaded = CompletableDeferred<Unit>()
        val results = mutableMapOf<String, Result<Int>>()
        val job = launch {
            loadHomeSections(linkedMapOf<String, suspend () -> Int>(
                "Next up" to { throw InvalidStatusException(500) },
                "Libraries" to { 3 },
                "Latest" to { slow.await(); 5 },
            )) { name, result ->
                results[name] = result
                if (name == "Libraries") libraryLoaded.complete(Unit)
            }
        }
        withTimeout(2000) { libraryLoaded.await() }
        assertEquals(3, results["Libraries"]?.getOrThrow())
        assertFalse(job.isCompleted)
        slow.complete(Unit)
        job.join()
        assertTrue(results["Next up"]!!.isFailure)
        assertEquals(5, results["Latest"]?.getOrThrow())
    }

    @Test
    fun `leaving the screen cancels requests without publishing old session results`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var published = false
        var cancelled = false
        val job = launch {
            loadHomeSections(mapOf<String, suspend () -> Int>("Libraries" to {
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled = true }
            })) { _, _ -> published = true }
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(cancelled)
        assertFalse(published)
    }

    @Test
    fun `diagnostics distinguish authentication and decoding without exposing exception text`() {
        assertTrue(homeFailureReason(InvalidStatusException(401)).contains("401"))
        assertTrue(homeFailureReason(InvalidStatusException(403)).contains("403"))
        assertTrue(homeFailureReason(InvalidContentException()).contains("compatibility"))
        assertFalse(homeFailureReason(IllegalStateException("secret-token")).contains("secret-token"))
    }
}
