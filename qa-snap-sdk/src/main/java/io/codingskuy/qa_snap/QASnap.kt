package io.codingskuy.qa_snap

import android.app.Application
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import kotlin.jvm.java

/**
 * Annotation to enable automatic QA recording for activities or application
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class EnableQARecording(
    /**
     * Whether to start recording automatically when activity starts
     */
    val autoStart: Boolean = true,

    /**
     * Log level for capture (V, D, I, W, E, F, S)
     */
    val logLevel: String = "I",

    /**
     * Build types where QA recording should be enabled
     * Empty array means enabled for all build types
     */
    val buildTypes: Array<String> = []
)

/**
 * QASnap - Main SDK utilities and shortcuts
 */
object QASnap {

    /**
     * Quick start QA recording with one line of code
     * @param activity The activity context
     * @param autoStart Whether to start recording automatically
     * @return QASnapHelper instance for further customization
     */
    fun start(activity: AppCompatActivity, autoStart: Boolean = true): QASnapHelper {
        return QASnapHelper.quickStart(activity, autoStart)
    }

    /**
     * Enable QA recording globally for debug builds
     * Call this in your Application.onCreate()
     */
    fun enableForDebug(application: Application) {
        if (isDebugBuild()) {
            // Could implement global activity lifecycle callbacks here
            // For now, developers should use QASnapActivity or QASnapHelper
        }
    }

    /**
     * Create QA recording session with custom settings
     */
    fun createSession(activity: AppCompatActivity): QASnapSessionBuilder {
        return QASnapSessionBuilder(activity)
    }

    private fun isDebugBuild(): Boolean {
        return try {
            // Try to access BuildConfig.DEBUG from the host app
            val buildConfigClass = Class.forName("BuildConfig")
            val debugField = buildConfigClass.getField("DEBUG")
            debugField.getBoolean(null)
        } catch (e: Exception) {
            // Default to false if can't determine debug status
            false
        }
    }
}

/**
 * Builder pattern for creating customized QA recording sessions
 */
class QASnapSessionBuilder(private val activity: AppCompatActivity) {
    private var autoStart = true
    private var logLevel = "I"
    private var tagFilter: String? = null
    private var packageFilter: String? = null
    private var onReady: (() -> Unit)? = null
    private var onComplete: ((File?, File?) -> Unit)? = null

    fun autoStart(enabled: Boolean) = apply { autoStart = enabled }
    fun logLevel(level: String) = apply { logLevel = level }
    fun tagFilter(filter: String?) = apply { tagFilter = filter }
    fun packageFilter(filter: String?) = apply { packageFilter = filter }
    fun onReady(callback: () -> Unit) = apply { onReady = callback }
    fun onComplete(callback: (File?, File?) -> Unit) = apply { onComplete = callback }

    fun build(): QASnapHelper {
        val helper = QASnapHelper(activity).apply {
            initialize(autoStart)
            onReady?.let { onRecordingReady(it) }
            onComplete?.let { onComplete(it) }
        }

        // Apply custom settings to recorder if needed
        if (logLevel != "I" || tagFilter != null || packageFilter != null) {
            helper.getRecorder()?.let { recorder ->
                // Could implement custom settings here
                // For now, users can access recorder directly
            }
        }

        return helper
    }
}