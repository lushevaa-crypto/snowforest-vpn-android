package com.zaneschepke.wireguardautotunnel.routing

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Хранит кэш списка RU подсетей в файловой системе приложения.
 * Файл: filesDir/smart_routing_cache.txt
 * Формат: первая строка — timestamp (мс), остальные — подсети
 */
object RouteCache {

    private const val TAG = "SF_RouteCache"
    private const val CACHE_FILE = "smart_routing_cache.txt"
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L // 24 часа

    data class CacheResult(
        val prefixes: List<String>,
        val isStale: Boolean,
        val ageMs: Long,
    )

    /**
     * Читает кэш. Возвращает null если кэша нет.
     */
    fun read(context: Context): CacheResult? {
        return try {
            val file = File(context.filesDir, CACHE_FILE)
            if (!file.exists()) {
                Log.d(TAG, "Cache file not found")
                return null
            }

            val lines = file.readLines()
            if (lines.isEmpty()) return null

            val timestamp = lines[0].toLongOrNull() ?: return null
            val prefixes = lines.drop(1).filter { it.isNotBlank() }

            if (prefixes.isEmpty()) return null

            val ageMs = System.currentTimeMillis() - timestamp
            val isStale = ageMs > MAX_AGE_MS

            Log.d(TAG, "Cache: ${prefixes.size} prefixes, age=${ageMs / 1000}s, stale=$isStale")

            CacheResult(prefixes, isStale, ageMs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cache: ${e.message}")
            null
        }
    }

    /**
     * Сохраняет список подсетей в кэш с текущим timestamp.
     */
    fun write(context: Context, prefixes: List<String>) {
        try {
            val file = File(context.filesDir, CACHE_FILE)
            val lines = mutableListOf<String>()
            lines.add(System.currentTimeMillis().toString())
            lines.addAll(prefixes)
            file.writeText(lines.joinToString("\n"))
            Log.d(TAG, "Cache saved: ${prefixes.size} prefixes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write cache: ${e.message}")
        }
    }
}
