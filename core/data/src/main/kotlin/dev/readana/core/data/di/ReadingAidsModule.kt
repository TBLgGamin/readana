package dev.readana.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.readana.core.data.dictionary.DictionaryLookup
import dev.readana.core.data.dictionary.WiktionaryDictionary
import dev.readana.core.data.notes.FileNotesExporter
import dev.readana.core.data.notes.NotesExporter

/**
 * The things that help someone read rather than store what they read: looking a word up, and taking
 * their notes somewhere else. Kept apart from [DataModule], which binds the repositories.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReadingAidsModule {
    @Binds
    abstract fun bindsNotesExporter(impl: FileNotesExporter): NotesExporter

    @Binds
    abstract fun bindsDictionaryLookup(impl: WiktionaryDictionary): DictionaryLookup
}
