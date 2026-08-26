package kz.maestrosultan.fitjournal.testing

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.internal.MainDispatcherFactory

/**
 * Gives the JVM test target a real `Dispatchers.Main`.
 *
 * There is no platform Main dispatcher on plain JVM — Android and iOS have one,
 * this target does not. `Dispatchers.setMain()` papers over that for the span of
 * a test, but `resetMain()` puts back the *missing* one, and any coroutine still
 * unwinding at that moment throws when it tries to resume:
 *
 * ```
 * Exception in thread "DefaultDispatcher-worker-3" IllegalStateException:
 *   Dispatchers.Main was accessed when the platform dispatcher was absent
 *   and the test dispatcher was unset
 * ```
 *
 * Nothing catches that — it lands on the global handler and `runTest` reports it
 * against **whatever suite happens to start next**, as an
 * `UncaughtExceptionsBeforeTest` with no connection to the code that caused it.
 * That is a genuinely nasty failure mode: the test that goes red is never the
 * test that is wrong.
 *
 * The window is real and unavoidable in this codebase: every DB datasource is
 * `withContext(Dispatchers.IO)`, so a ViewModel whose scope is Main and whose
 * repository is real hops off Main and back on every single call. Ordering the
 * teardown cannot close it, because the return hop happens on a pool thread that
 * the test thread has no way to await.
 *
 * So instead of racing it, make `Dispatchers.Main` always resolvable. Registered
 * through coroutines' own extension point at the LOWEST priority, so
 * `kotlinx-coroutines-test`'s factory still wraps it and `setMain()` /
 * `resetMain()` behave exactly as before — the only difference is that "reset"
 * now lands on something that works instead of something that throws.
 *
 * It delegates to [Dispatchers.Default]: tests that care about main-thread
 * semantics install their own dispatcher with `setMain()`, and this is only ever
 * the fallback outside those windows.
 */
@OptIn(InternalCoroutinesApi::class)
internal class JvmTestMainDispatcherFactory : MainDispatcherFactory {

    /** Lowest, so the coroutines-test factory keeps priority and wraps this. */
    override val loadPriority: Int = Int.MIN_VALUE

    override fun createDispatcher(allFactories: List<MainDispatcherFactory>): MainCoroutineDispatcher =
        JvmTestMainDispatcher
}

private object JvmTestMainDispatcher : MainCoroutineDispatcher() {

    override val immediate: MainCoroutineDispatcher get() = this

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        Dispatchers.Default.dispatch(context, block)
    }

    override fun toString(): String = "JvmTestMain"
}
