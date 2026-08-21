package dev.readana.core.common.coroutines

import javax.inject.Qualifier

/** Hilt qualifier selecting a [kotlinx.coroutines.CoroutineDispatcher] by role. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val readanaDispatcher: ReadanaDispatchers)

enum class ReadanaDispatchers {
    Default,
    IO
}

/** Hilt qualifier for the application-wide [kotlinx.coroutines.CoroutineScope]. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
