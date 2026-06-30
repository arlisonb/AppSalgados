package com.ionasalgados.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ionasalgados.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "server_config")

@Singleton
class ServerConfigStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val WHATSAPP_PHONE = stringPreferencesKey("whatsapp_phone")
        private val PRINTER_MAC = stringPreferencesKey("printer_mac")
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SERVER_URL] ?: BuildConfig.SOCKET_URL
    }

    suspend fun getServerUrl(): String {
        return context.dataStore.data.first()[SERVER_URL] ?: BuildConfig.SOCKET_URL
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[SERVER_URL] = url.trimEnd('/') }
    }

    suspend fun getWhatsAppPhone(): String {
        return context.dataStore.data.first()[WHATSAPP_PHONE] ?: ""
    }

    suspend fun setWhatsAppPhone(phone: String) {
        context.dataStore.edit { it[WHATSAPP_PHONE] = phone.replace(Regex("\\D"), "") }
    }

    suspend fun getPrinterMac(): String {
        return context.dataStore.data.first()[PRINTER_MAC] ?: ""
    }

    suspend fun setPrinterMac(mac: String) {
        context.dataStore.edit { it[PRINTER_MAC] = mac }
    }

    fun apiBaseUrl(url: String): String {
        val base = url.trimEnd('/')
        return if (base.endsWith("/api")) "$base/" else "$base/api/"
    }
}
