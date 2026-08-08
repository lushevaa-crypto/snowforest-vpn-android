package com.zaneschepke.wireguardautotunnel.routing

import android.content.Context
import android.util.Log

/**
 * Применяет сгенерированные маршруты к quickConfig строке туннеля.
 * Pipeline: RouteSource → AllowedIPsGenerator → RouteValidator → TunnelConfigUpdater
 */
object TunnelConfigUpdater {

    private const val TAG = "SF_TunnelConfigUpdater"

    fun applySmartRouting(context: Context, quickConfig: String): UpdateResult {
        Log.d(TAG, "=== Snow Forest Smart Routing START ===")
        Log.d(TAG, "Config length: ${quickConfig.length}")

        if (hasSmartRouting(quickConfig)) {
            Log.d(TAG, "Smart Routing already applied, skipping")
            return UpdateResult.AlreadyPatched
        }

        val originalAllowedIPs = extractAllowedIPs(quickConfig)
        Log.d(TAG, "Original AllowedIPs: $originalAllowedIPs")

        val source = StaticRouteSource(context)
        val generatorResult = AllowedIPsGenerator.generate(source)
        Log.d(TAG, "Generator result: ${generatorResult.routeCount} routes, isFallback=${generatorResult.isFallback}")

        when (val validation = RouteValidator.validate(generatorResult)) {
            is RouteValidator.ValidationResult.Error -> {
                Log.e(TAG, "Validation failed: ${validation.reason}")
                return UpdateResult.ValidationFailed(validation.reason)
            }
            is RouteValidator.ValidationResult.Ok -> {
                Log.d(TAG, "Validation OK")
            }
        }

        val newAllowedIPs = generatorResult.allowedIPs.joinToString(", ")
        val patchedConfig = replaceAllowedIPs(quickConfig, newAllowedIPs)

        Log.d(TAG, "=== Smart Routing DONE: ${generatorResult.routeCount} routes applied ===")

        return UpdateResult.Success(
            patchedConfig = patchedConfig,
            originalAllowedIPs = originalAllowedIPs ?: "0.0.0.0/0, ::/0",
            routeCount = generatorResult.routeCount,
            source = generatorResult.source,
        )
    }

    private fun hasSmartRouting(quickConfig: String): Boolean {
        val allowedIPs = extractAllowedIPs(quickConfig) ?: return false
        val commaCount = allowedIPs.count { it == ',' }
        Log.d(TAG, "hasSmartRouting check: commaCount=$commaCount")
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
