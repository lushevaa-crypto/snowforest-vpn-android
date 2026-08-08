package com.zaneschepke.wireguardautotunnel.routing

import android.util.Log

/**
 * Валидирует сгенерированный список маршрутов.
 */
object RouteValidator {

    private const val TAG = "SF_RouteValidator"
    private const val MIN_ROUTES = 5
    private const val MAX_ROUTES = 30_000

    fun validate(result: AllowedIPsGenerator.GeneratorResult): ValidationResult {
        Log.d(TAG, "Validating ${result.routeCount} routes from ${result.source}, isFallback=${result.isFallback}")

        if (result.isFallback) {
            Log.w(TAG, "Using fallback routes — validation skipped")
            return ValidationResult.Ok
        }

        if (result.routeCount < MIN_ROUTES) {
            val msg = "Too few routes: ${result.routeCount} (min $MIN_ROUTES)"
            Log.e(TAG, msg)
            return ValidationResult.Error(msg)
        }

        if (result.routeCount > MAX_ROUTES) {
            val msg = "Too many routes: ${result.routeCount} (max $MAX_ROUTES)"
            Log.e(TAG, msg)
            return ValidationResult.Error(msg)
        }

        Log.d(TAG, "Validation OK: ${result.routeCount} routes")
        return ValidationResult.Ok
    }

    sealed class ValidationResult {
        object Ok : ValidationResult()
        data class Error(val reason: String) : ValidationResult()
    }
}
