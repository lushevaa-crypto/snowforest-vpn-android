package com.zaneschepke.wireguardautotunnel.routing

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.zaneschepke.wireguardautotunnel.data.DataStoreManager
import com.zaneschepke.wireguardautotunnel.domain.repository.InstalledPackageRepository
import org.json.JSONArray

/**
 * Snow Forest VPN — инициализация App Bypass
 *
 * Запускается ОДИН РАЗ при первом старте.
 * Читает default_bypass_apps.json → проверяет установленные пакеты
 * → сохраняет только существующие в DataStore.
 *
 * Туннели и конфиги НЕ изменяет.
 * DataStore — единственное место хранения bypass_packages.
 */
class BypassAppsInitializer(
    private val context: Context,
    private val dataStoreManager: DataStoreManager,
    private val packageRepository: InstalledPackageRepository,
) {
    companion object {
        val bypassAppsInitialized = booleanPreferencesKey("bypass_apps_initialized")
        val bypassPackages = stringSetPreferencesKey("bypass_packages")
        private const val TAG = "SF_BypassApps"
    }

    suspend fun initializeIfNeeded() {
        val alreadyInitialized = dataStoreManager.getFromStore(bypassAppsInitialized) == true
        if (alreadyInitialized) {
            Log.d(TAG, "Already initialized, skipping")
            return
        }

        Log.i(TAG, "First launch — initializing bypass apps")

        val defaultPackages = loadDefaultPackages()
        if (defaultPackages.isEmpty()) {
            Log.e(TAG, "Failed to load default_bypass_apps.json")
            return
        }

        // Получаем установленные пакеты и фильтруем
        val installed = packageRepository.getInstalledPackages()
            .map { it.packageName }
            .toSet()

        val toSave = defaultPackages.filter { it in installed }.toSet()

        Log.i(TAG, "Default packages: ${defaultPackages.size}, " +
            "installed: ${installed.size}, saving: ${toSave.size}")
        Log.d(TAG, "Saving bypass packages: $toSave")

        dataStoreManager.saveToDataStore(bypassPackages, toSave)
        dataStoreManager.saveToDataStore(bypassAppsInitialized, true)

        Log.i(TAG, "Bypass apps initialization complete")
    }

    /**
     * Загружает все package ID из default_bypass_apps.json.
     * Формат: [{"name": "Сбербанк", "packages": ["ru.sberbankmobile"]}, ...]
     */
    private fun loadDefaultPackages(): List<String> {
        return try {
            val json = context.assets.open("default_bypass_apps.json")
                .bufferedReader().readText()
            val array = JSONArray(json)
            val packages = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pkgArray = obj.getJSONArray("packages")
                for (j in 0 until pkgArray.length()) {
                    packages.add(pkgArray.getString(j))
                }
            }
            packages
        } catch (e: Exception) {
            Log.e(TAG, "Error loading default_bypass_apps.json: ${e.message}")
            emptyList()
        }
    }
}
