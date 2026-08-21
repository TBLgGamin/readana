package dev.readana.core.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.readana.core.data.downloads.DownloadsDirectory
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadsModule {
    /**
     * Partial files stay app-internal until the import step copies them into the library, so a
     * half-finished transfer never shows up in the user's SAF folder.
     */
    @Provides
    @Singleton
    @DownloadsDirectory
    fun providesDownloadsDirectory(@ApplicationContext context: Context): File = File(context.filesDir, "downloads")
}
