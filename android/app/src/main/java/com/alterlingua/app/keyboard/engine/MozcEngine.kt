package com.alterlingua.app.keyboard.engine

import android.content.Context
import com.google.android.apps.inputmethod.libs.mozc.session.MozcJni
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands
import org.mozc.android.inputmethod.japanese.protobuf.ProtoConfig
import java.io.File

/**
 * Japanese conversion with Mozc (BSD-3-Clause), running on the phone. Typed romaji or kana becomes hiragana in the field's
 * composing text and the candidates are kanji and kana conversions. History learning is off (incognito) and nothing is logged.
 *
 * Not thread-safe: use from the keyboard's main thread. [available] is false when the native library or its data could not be loaded.
 */
class MozcEngine(private val context: Context) : CandidateEngine {

    private var sessionId: Long = 0
    private var candidateIds: List<Int> = emptyList()

    /** True once the library, its data and a session are ready. Tried once, on first use. */
    val available: Boolean by lazy { start() }

    private fun start(): Boolean = try {
        System.loadLibrary("mozc")
        if (!MozcJni.initialize()) return false
        val directory = File(context.filesDir, "mozc").apply { mkdirs() }
        val data = File(directory, "mozc.data")
        // The data file ships inside the APK; copy it out once (the native code needs a real file path).
        context.assets.open("mozc/mozc.data").use { input ->
            val expected = context.assets.openFd("mozc/mozc.data").use { it.length }
            if (!data.exists() || data.length() != expected) data.outputStream().use { input.copyTo(it) }
        }
        if (!MozcJni.onPostLoad(File(directory, "profile").apply { mkdirs() }.absolutePath, data.absolutePath)) return false
        createSession()
    } catch (_: UnsatisfiedLinkError) {
        false
    } catch (_: java.io.IOException) {
        false
    }

    private fun createSession(): Boolean {
        val created = send(
            ProtoCommands.Input.newBuilder()
                .setType(ProtoCommands.Input.CommandType.CREATE_SESSION)
                .setCapability(
                    ProtoCommands.Capability.newBuilder()
                        .setTextDeletion(ProtoCommands.Capability.TextDeletionCapabilityType.DELETE_PRECEDING_TEXT),
                ),
        ) ?: return false
        sessionId = created.id
        // Privacy: no history, no learning saved on the phone.
        send(
            input(ProtoCommands.Input.CommandType.SET_CONFIG)
                .setConfig(ProtoConfig.Config.newBuilder().setIncognitoMode(true).setHistoryLearningLevel(ProtoConfig.Config.HistoryLearningLevel.NO_HISTORY)),
        )
        // The phone keyboard style: suggestions while typing.
        send(
            input(ProtoCommands.Input.CommandType.SET_REQUEST)
                .setRequest(ProtoCommands.Request.newBuilder().setMixedConversion(true).setZeroQuerySuggestion(false)),
        )
        return sessionId != 0L
    }

    private fun input(type: ProtoCommands.Input.CommandType) = ProtoCommands.Input.newBuilder().setType(type).setId(sessionId)

    private fun send(builder: ProtoCommands.Input.Builder): ProtoCommands.Output? {
        val command = ProtoCommands.Command.newBuilder().setInput(builder).build()
        val answer = MozcJni.evalCommand(command.toByteArray())
        return ProtoCommands.Command.parseFrom(answer).output
    }

    private fun key(event: ProtoCommands.KeyEvent.Builder): Composition {
        if (!available) return Composition()
        val output = send(
            input(ProtoCommands.Input.CommandType.SEND_KEY)
                .setKey(event.setMode(ProtoCommands.CompositionMode.HIRAGANA).setActivated(true)),
        ) ?: return Composition()
        return read(output)
    }

    private fun read(output: ProtoCommands.Output): Composition {
        val preedit = output.preedit.segmentList.joinToString("") { it.value }
        val window = output.candidateWindow
        candidateIds = window.candidateList.map { it.id }
        return Composition(preedit, window.candidateList.map { it.value })
    }

    override fun append(input: String): Composition {
        val event = ProtoCommands.KeyEvent.newBuilder()
        val ch = input.singleOrNull()
        if (ch != null && ch.code < 128) event.keyCode = ch.code
        else event.setKeyString(input).setInputStyle(ProtoCommands.KeyEvent.InputStyle.AS_IS)
        return key(event)
    }

    override fun deleteLast(): Composition =
        key(ProtoCommands.KeyEvent.newBuilder().setSpecialKey(ProtoCommands.KeyEvent.SpecialKey.BACKSPACE))

    override fun choose(index: Int): Choice {
        val id = candidateIds.getOrNull(index) ?: return Choice("")
        val output = send(
            input(ProtoCommands.Input.CommandType.SEND_COMMAND)
                .setCommand(ProtoCommands.SessionCommand.newBuilder().setType(ProtoCommands.SessionCommand.CommandType.SUBMIT_CANDIDATE).setId(id)),
        ) ?: return Choice("")
        return Choice(output.result.value, read(output))
    }

    override fun reset() {
        candidateIds = emptyList()
        if (!available) return
        send(
            input(ProtoCommands.Input.CommandType.SEND_COMMAND)
                .setCommand(ProtoCommands.SessionCommand.newBuilder().setType(ProtoCommands.SessionCommand.CommandType.REVERT)),
        )
    }
}
