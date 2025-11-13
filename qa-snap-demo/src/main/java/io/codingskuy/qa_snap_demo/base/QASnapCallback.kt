package io.codingskuy.qa_snap_demo.base

import java.io.File

/**
 * QASnapCallback - Interface for QA Snap recording callbacks
 */
interface QASnapCallback {
    fun shouldAutoStartRecording(): Boolean
    fun onQARecordingReady()
    fun onQARecordingStarted()
    fun onQARecordingComplete(videoFile: File?, logFile: File?)
    fun onQARecordingError(error: String)
}