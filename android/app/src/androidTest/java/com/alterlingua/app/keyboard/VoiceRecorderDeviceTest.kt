package com.alterlingua.app.keyboard

import android.Manifest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs on a real device (an emulator cannot record): records one second with the real microphone and checks the file is
 * in a format the backend accepts. It grants the microphone permission to the app for the test.
 */
class VoiceRecorderDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun recordsAnMp4AudioFile_theBackendAccepts_andDeletesIt() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        val files = VoiceFiles.forContext(context)
        val recorder = MediaRecorderVoiceRecorder(context)
        val file = files.newFile()

        val started = recorder.start(file)
        assumeTrue("this device cannot start the microphone (emulator?)", started)
        Thread.sleep(1_200)
        recorder.amplitude()
        assertTrue(recorder.stop())

        assertTrue(file.length() > 500)
        val head = file.inputStream().use { it.readNBytes(12) }
        assertEquals("ftyp", String(head, 4, 4, Charsets.ISO_8859_1)) // an MP4 container, like the backend expects
        files.delete(file)
        assertTrue(!file.exists())
    }

    @Test
    fun sweepingRemovesLeftovers() {
        val files = VoiceFiles.forContext(context)
        val left: File = files.newFile().also { it.writeBytes(byteArrayOf(1)) }
        files.sweep()
        assertTrue(!left.exists())
    }
}
