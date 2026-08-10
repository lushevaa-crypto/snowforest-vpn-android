package com.zaneschepke.wireguardautotunnel.routing

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.zaneschepke.wireguardautotunnel.data.DataStoreManager
import com.zaneschepke.wireguardautotunnel.domain.repository.TunnelRepository
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.state.SplitOption
import com.zaneschepke.wireguardautotunnel.ui.state.EditableConfig
import com.zaneschepke.wireguardautotunnel.ui.state.EditableInterface
import kotlinx.coroutines.flow.first
import org.json.JSONArray

/**
 * Snow Forest VPN — инициализация App Bypass
 *
 * Запускается один раз при первом старте приложения.
 * Читает default_bypass_apps.json из assets и применяет
 * excludedApplications ко всем туннелям.
 *
 * После инициализации флаг bypass_apps_initialized сохраняется в DataStore.
 * Пользовательские изменения не перезаписываются никогда.
 */
class BypassAppsInitializer(
    private val context: Context,
    private val dataStoreManager: DataStoreManager,
    private val tunnelRepository: TunnelRepository,
) {

    private val TAG = "SF_BypassApps"

    companion object {
        val bypassAppsInitialized = booleanPreferencesKey("bypass_apps_initialized")
    }

    suspend fun initializeIfNeeded() {
        val alreadyInitialized = dataStoreManager.getFromStore(bypassAppsInitialized) == true
        if (alreadyInitialized) {
            Log.d(TAG, "Bypass apps already initialized, skipping")
            return
        }

        Log.i(TAG, "First launch — initializing bypass apps")

        val defaultPackages = loadDefaultBypassApps()
        if (defaultPackages.isEmpty()) {
            Log.e(TAG, "Failed to load default_bypass_apps.json")
            return
        }

        Log.i(TAG, "Loaded ${defaultPackages.size} default bypass apps")

        // Применяем ко всем существующим туннелям
        val tunnels = tunnelRepository.flow.first()
        tunnels.forEach { tunnel ->
            try {
                val config = tunnel.getConfig()
                // Не перезаписываем если пользователь уже настроил исключения
                val hasExistingExclusions = config.`interface`.excludedApplications?.isNotEmpty() == true
                val hasExistingInclusions = config.`interface`.includedApplications?.isNotEmpty() == true
                if (hasExistingExclusions || hasExistingInclusions) {
                    Log.d(TAG, "Tunnel ${tunnel.name} already has split tunnel config, skipping")
                    return@forEach
                }

                val editableConfig = EditableConfig.from(config)
                val editableInterface = EditableInterface.from(config.`interface`)
                val updatedInterface = editableInterface.copy(
                    excludedApplications = defaultPackages.toSet(),
                    includedApplications = emptySet(),
                )
                val updatedConfig = editableConfig.copy(`interface` = updatedInterface).buildConfig()
                tunnelRepository.save(
                    tunnel.copy(quickConfig = updatedConfig.withName(tunnel.name).asQuickString())
                )
                Log.i(TAG, "Applied ${defaultPackages.size} bypass apps to tunnel: ${tunnel.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply bypass apps to tunnel ${tunnel.name}: ${e.message}")
            }
        }

        dataStoreManager.saveToDataStore(bypassAppsInitialized, true)
        Log.i(TAG, "Bypass apps initialization complete")
    }

    private fun loadDefaultBypassApps(): List<String> {
        return try {
            val json = context.assets.open("default_bypass_apps.json")
                .bufferedReader().readText()
            val array = JSONArray(json)
            val packages = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                packages.add(obj.getString("package"))
            }
            packages
        } catch (e: Exception) {
            Log.e(TAG, "Error loading default_bypass_apps.json: ${e.message}")
            emptyList()
        }
    }
}
