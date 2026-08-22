package dev.readana.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.readana.core.data.messages.DefaultMessageBus
import dev.readana.core.data.messages.MessageBus

/** The one place passing messages are collected on their way to the app shell. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MessagesModule {
    @Binds
    abstract fun bindsMessageBus(impl: DefaultMessageBus): MessageBus
}
