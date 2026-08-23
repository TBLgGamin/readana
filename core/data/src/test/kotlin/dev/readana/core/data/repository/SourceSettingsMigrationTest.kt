package dev.readana.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.readana.core.model.CatalogRef
import dev.readana.core.model.StoredQuota
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

/**
 * Source configuration used to be five fields on `SourceSettings`, all of them Anna's. An install
 * from before that has to keep its mirrors and its membership key, so the old preference keys are
 * folded into the generic map on the first read and dropped on the first write.
 */
@RunWith(AndroidJUnit4::class)
class SourceSettingsMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scope: CoroutineScope
    private lateinit var store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var repository: DataStoreSettingsRepository
    private lateinit var file: File

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File(context.filesDir, "source-migration-test.preferences_pb").apply { delete() }
        store = PreferenceDataStoreFactory.create(scope = scope) { file }
        repository = DataStoreSettingsRepository(store)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `settings written by an older build are read as generic source configuration`() = runTest {
        writeLegacyAnnasSettings()

        val settings = repository.sourceSettings.first()

        val annas = settings.configFor(ANNAS_ID)
        assertEquals("https://one.test\nhttps://two.test", annas["mirrors"])
        assertEquals("s3cr3t", annas["secretKey"])
        assertEquals("https://two.test", annas["preferredMirror"])
        assertEquals(StoredQuota(remaining = 7, perDay = 100), settings.quotas[ANNAS_ID])
    }

    @Test
    fun `the old keys are gone once anything is written`() = runTest {
        writeLegacyAnnasSettings()

        // Any write at all rewrites the whole source block.
        repository.updateSourceSettings { it.withSourceEnabled("openlibrary", false) }

        val raw = store.data.first()
        assertNull(raw[stringPreferencesKey("source_annas_mirrors")])
        assertNull(raw[stringPreferencesKey("source_annas_secret_key")])
        assertNull(raw[intPreferencesKey("source_annas_downloads_left")])
        // ...and what they held is still there, under the generic keys.
        val settings = repository.sourceSettings.first()
        assertEquals("s3cr3t", settings.configFor(ANNAS_ID)["secretKey"])
        assertEquals(7, settings.quotas[ANNAS_ID]?.remaining)
    }

    @Test
    fun `a value already stored generically wins over the old key`() = runTest {
        writeLegacyAnnasSettings()

        repository.updateSourceSettings { it.withConfigValue(ANNAS_ID, "secretKey", "newer") }

        assertEquals("newer", repository.sourceSettings.first().configFor(ANNAS_ID)["secretKey"])
    }

    @Test
    fun `catalogs survive a write and read`() = runTest {
        val catalog = CatalogRef(
            id = "catalog-standard-ebooks",
            name = "Standard Ebooks",
            feedUrl = "https://standardebooks.test/feeds/opds",
            presetId = "standard-ebooks"
        )

        repository.updateSourceSettings { it.withCatalog(catalog) }

        assertEquals(listOf(catalog), repository.sourceSettings.first().catalogs)
    }

    @Test
    fun `forgetting a catalog forgets what was configured for it`() = runTest {
        repository.updateSourceSettings {
            it.withCatalog(CatalogRef("catalog-1", "Mine", "https://mine.test/opds"))
                .withConfigValue("catalog-1", "username", "ada")
                .withSourceEnabled("catalog-1", false)
        }

        repository.updateSourceSettings { it.withoutCatalog("catalog-1") }

        val settings = repository.sourceSettings.first()
        assertEquals(emptyList(), settings.catalogs)
        assertEquals(emptyMap(), settings.configFor("catalog-1"))
        // Nothing refers to the catalog any more, so its switch should not linger either.
        assertEquals(true, settings.isEnabled("catalog-1", enabledByDefault = true))
    }

    private suspend fun writeLegacyAnnasSettings() {
        store.edit { preferences ->
            preferences[stringPreferencesKey("source_annas_mirrors")] =
                "https://one.test\nhttps://two.test"
            preferences[stringPreferencesKey("source_annas_secret_key")] = "s3cr3t"
            preferences[stringPreferencesKey("source_annas_preferred_mirror")] = "https://two.test"
            preferences[intPreferencesKey("source_annas_downloads_left")] = 7
            preferences[intPreferencesKey("source_annas_downloads_per_day")] = 100
        }
    }

    private companion object {
        const val ANNAS_ID = "annas-archive"
    }
}
