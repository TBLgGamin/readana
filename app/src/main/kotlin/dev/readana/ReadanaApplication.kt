package dev.readana

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Provider
import timber.log.Timber

/**
 * WorkManager is initialised on demand (its default initialiser is removed in the manifest) so that
 * download workers can be constructed by Hilt.
 */
@HiltAndroidApp
class ReadanaApplication :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** Lazily resolved: Coil may ask for a loader before or after Hilt has finished injecting. */
    @Inject
    lateinit var httpClient: Provider<HttpClient>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
            .build()

    /**
     * Covers come from two places: files the importer extracted, and search results that link to
     * remote art. Coil ships no network fetcher by default, so without this the remote ones fail
     * silently and every result falls back to its title card.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(KtorNetworkFetcherFactory(httpClient = { httpClient.get() })) }
        .memoryCache {
            MemoryCache.Builder().maxSizePercent(context, MEMORY_CACHE_FRACTION).build()
        }
        // Cover art rarely changes, and a browsing session revisits the same books repeatedly.
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve(COVER_CACHE_DIR))
                .maxSizeBytes(COVER_CACHE_BYTES)
                .build()
        }
        .crossfade(true)
        .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private companion object {
        const val MEMORY_CACHE_FRACTION = 0.20
        const val COVER_CACHE_DIR = "cover_cache"
        const val COVER_CACHE_BYTES = 64L * 1024 * 1024
    }
}
