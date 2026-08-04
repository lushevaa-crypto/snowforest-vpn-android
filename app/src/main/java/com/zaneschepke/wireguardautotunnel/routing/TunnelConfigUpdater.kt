package com.zaneschepke.wireguardautotunnel.routing

import android.content.Context
import timber.log.Timber

/**
 * Применяет сгенерированные маршруты к quickConfig строке туннеля.
 *
 * Pipeline:
 * RouteSource → AllowedIPsGenerator → RouteValidator → TunnelConfigUpdater
 */
object TunnelConfigUpdater {

    private const val TAG = "TunnelConfigUpdater"

    /**
     * Основная точка входа. Вызывается при импорте конфига.
     *
     * @return UpdateResult с пропатченным конфигом или оригиналом при ошибке
     */
    fun applySmartRouting(context: Context, quickConfig: String): UpdateResult {
        Timber.tag(TAG).d("=== Snow Forest Smart Routing ===")

        // Проверка: уже применён Smart Routing?
        if (hasSmartRouting(quickConfig)) {
            Timber.tag(TAG).d("Smart Routing already applied, skipping")
            return UpdateResult.AlreadyPatched
        }

        val originalAllowedIPs = extractAllowedIPs(quickConfig)
        Timber.tag(TAG).d("Original AllowedIPs: $originalAllowedIPs")

        // Pipeline
        val source = StaticRouteSource(context)
        val generatorResult = AllowedIPsGenerator.generate(source)

        when (val validation = RouteValidator.validate(generatorResult)) {
            is RouteValidator.ValidationResult.Error -> {
                Timber.tag(TAG).e("Validation failed: ${validation.reason}, keeping original config")
                return UpdateResult.ValidationFailed(validation.reason)
            }
            is RouteValidator.ValidationResult.Ok -> Unit
        }

        val newAllowedIPs = generatorResult.allowedIPs.joinToString(", ")
        val patchedConfig = replaceAllowedIPs(quickConfig, newAllowedIPs)

        Timber.tag(TAG).d(
            """
            Applied: OK
            Route source: ${generatorResult.source}
            Generated routes: ${generatorResult.routeCount}
            Generation: ${generatorResult.generationMs}ms
            """.trimIndent()
        )

        return UpdateResult.Success(
            patchedConfig = patchedConfig,
            originalAllowedIPs = originalAllowedIPs ?: "0.0.0.0/0, ::/0",
            routeCount = generatorResult.routeCount,
            source = generatorResult.source,
        )
    }

    private fun hasSmartRouting(quickConfig: String): Boolean {
        val allowedIPs = extractAllowedIPs(quickConfig) ?: return false
        // Если AllowedIPs содержит много маршрутов — уже пропатчен
        val commaCount = allowedIPs.count { it == ',' }
        return commaCount > 10
    }

    private fun extractAllowedIPs(quickConfig: String): String? =
        quickConfig.lines()
            .find { it.trim().startsWith("AllowedIPs") }
            ?.substringAfter("=")
            ?.trim()

    private fun replaceAllowedIPs(quickConfig: String, newAllowedIPs: String): String =
        quickConfig.lines().joinToString("\n") { line ->
            if (line.trim().startsWith("AllowedIPs")) "AllowedIPs = $newAllowedIPs" else line
        }

    sealed class UpdateResult {
        data class Success(
            val patchedConfig: String,
            val originalAllowedIPs: String,
            val routeCount: Int,
            val source: String,
        ) : UpdateResult()

        object AlreadyPatched : UpdateResult()

        data class ValidationFailed(val reason: String) : UpdateResult()
    }
}
