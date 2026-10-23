package io.github.kdroidfilter.seforimapp.framework.startup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StartupPreparationTest {
    @Test
    fun slowLibraryPreparationLeavesTheEventThreadResponsive() =
        runBlocking {
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val preparation =
                async(Dispatchers.Swing) {
                    prepareStartup {
                        assertFalse(SwingUtilities.isEventDispatchThread())
                        started.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                    }
                }
            try {
                assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
                // This continuation must run while the importer is still waiting.
                withContext(Dispatchers.Swing) { assertFalse(preparation.isCompleted) }
            } finally {
                release.countDown()
            }
            assertTrue(preparation.await().isSuccess)
        }

    @Test
    fun failedPreparationCanBeRetried() =
        runBlocking {
            val failure = IllegalStateException("Database is locked")
            assertSame(failure, prepareStartup { throw failure }.exceptionOrNull())
            assertTrue(prepareStartup {}.isSuccess)
        }

    @Test
    fun cancellationIsPropagatedInsteadOfBecomingAStartupError() =
        runBlocking {
            assertFailsWith<CancellationException> {
                prepareStartup { throw CancellationException("Window closed") }
            }
            Unit
        }
}
