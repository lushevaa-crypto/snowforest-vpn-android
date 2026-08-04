package com.zaneschepke.wireguardautotunnel.routing

import android.content.Context
import timber.log.Timber

/**
 * Встроенный статический список российских подсетей.
 * Источник: assets/ru_prefixes.txt
 * Для POC — не требует сети, работает всегда.
 */
class StaticRouteSource(private val context: Context) : RouteSource {

    override val name: String = "StaticRouteSource"

    override fun loadRuPrefixes(): List<String> {
        return try {
            context.assets.open("ru_prefixes.txt")
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() }
                .also { Timber.d("[$name] Loaded ${it.size} RU prefixes") }
        } catch (e: Exception) {
            Timber.e(e, "[$name] Failed to load ru_prefixes.txt from assets")
            emptyList()
        }
    }
}
