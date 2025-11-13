package io.codingskuy.qa_snap_demo.utils

import android.content.Context
import android.util.Log
import io.codingskuy.qa_snap_demo.BuildConfig

/**
 * EnvironmentManager - Manages environment-specific configurations
 * 
 * This class centralizes all environment-specific logic including:
 * - Environment detection
 * - QA Snap SDK activation/deactivation
 * - API endpoints configuration
 * - Logging configuration
 * - Feature flags per environment
 */
object EnvironmentManager {
    
    private const val TAG = "EnvironmentManager"
    
    enum class Environment {
        DEVELOPMENT,
        STAGING, 
        PRODUCTION
    }
    
    /**
     * Get current environment from BuildConfig
     */
    fun getCurrentEnvironment(): Environment {
        return when (BuildConfig.ENVIRONMENT) {
            "development" -> Environment.DEVELOPMENT
            "staging" -> Environment.STAGING
            "production" -> Environment.PRODUCTION
            else -> Environment.DEVELOPMENT // Default fallback
        }
    }
    
    /**
     * Check if QA Snap should be enabled in current environment
     * QA Snap is ONLY enabled in STAGING environment
     */
    fun isQASnapEnabled(): Boolean {
        val enabled = BuildConfig.ENABLE_QA_SNAP
        val environment = getCurrentEnvironment()
        
        Log.d(TAG, "Environment: $environment")
        Log.d(TAG, "QA Snap enabled in BuildConfig: $enabled")
        Log.d(TAG, "Final QA Snap status: ${enabled && environment == Environment.STAGING}")
        
        // Double check: QA Snap should ONLY be enabled in staging
        return enabled && environment == Environment.STAGING
    }
    
    /**
     * Check if logging should be enabled
     */
    fun isLoggingEnabled(): Boolean {
        return BuildConfig.ENABLE_LOGGING
    }
    
    /**
     * Get API base URL for current environment
     */
    fun getBaseUrl(): String {
        return BuildConfig.BASE_URL
    }
    
    /**
     * Get environment display name
     */
    fun getEnvironmentDisplayName(): String {
        return when (getCurrentEnvironment()) {
            Environment.DEVELOPMENT -> "Development"
            Environment.STAGING -> "Staging"
            Environment.PRODUCTION -> "Production"
        }
    }
    
    /**
     * Get app name for current environment
     */
    fun getAppName(context: Context): String {
        return try {
            context.getString(context.resources.getIdentifier("app_name", "string", context.packageName))
        } catch (e: Exception) {
            "QA Snap Demo"
        }
    }
    
    /**
     * Log environment info (only if logging is enabled)
     */
    fun logEnvironmentInfo() {
        if (isLoggingEnabled()) {
            Log.d(TAG, "=== ENVIRONMENT INFO ===")
            Log.d(TAG, "Environment: ${getEnvironmentDisplayName()}")
            Log.d(TAG, "Base URL: ${getBaseUrl()}")
            Log.d(TAG, "QA Snap Enabled: ${isQASnapEnabled()}")
            Log.d(TAG, "Logging Enabled: ${isLoggingEnabled()}")
            Log.d(TAG, "Application ID: ${BuildConfig.APPLICATION_ID}")
            Log.d(TAG, "Version: ${BuildConfig.VERSION_NAME}")
            Log.d(TAG, "========================")
        }
    }
    
    /**
     * Get environment-specific features
     */
    fun getEnvironmentFeatures(): Map<String, Boolean> {
        return when (getCurrentEnvironment()) {
            Environment.DEVELOPMENT -> mapOf(
                "debug_tools" to true,
                "mock_data" to true,
                "crash_reporting" to false,
                "analytics" to false,
                "qa_recording" to false
            )
            Environment.STAGING -> mapOf(
                "debug_tools" to true,
                "mock_data" to false,
                "crash_reporting" to true,
                "analytics" to true,
                "qa_recording" to true  // QA Snap only in staging
            )
            Environment.PRODUCTION -> mapOf(
                "debug_tools" to false,
                "mock_data" to false,
                "crash_reporting" to true,
                "analytics" to true,
                "qa_recording" to false
            )
        }
    }
    
    /**
     * Check if a specific feature is enabled
     */
    fun isFeatureEnabled(featureName: String): Boolean {
        return getEnvironmentFeatures()[featureName] ?: false
    }
}