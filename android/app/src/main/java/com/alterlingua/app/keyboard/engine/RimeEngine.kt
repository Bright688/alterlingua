package com.alterlingua.app.keyboard.engine

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Chinese conversion with librime (BSD-3-Clause): pinyin, strokes or Zhuyin, all with Simplified characters, using the small
 * "pinyin_simp" dictionary (Apache-2.0,
 * derived from Android's own Pinyin IME). Everything runs on the phone; nothing is logged or sent.
 *
 * Not thread-safe: use from the keyboard's main thread. [available] is false when the libraries or schema could not be loaded.
 * The first start compiles the schema, which takes a few seconds: read [available] off the main thread.
 */
class RimeEngine(private val context: Context, private val schema: String = SCHEMA_PINYIN) : CandidateEngine {

    private var session: Long = 0

    val available: Boolean by lazy { start() }

    private fun start(): Boolean = try {
        System.loadLibrary("rime")
        System.loadLibrary("rimejni")
        val root = File(context.filesDir, "rime")
        val shared = File(root, "shared").apply { mkdirs() }
        val user = File(root, "user").apply { mkdirs() }
        copySchemaFiles(shared)
        RimeJni.start(shared.absolutePath, user.absolutePath, schema)
        session = RimeJni.createSession(schema)
        session != 0L
    } catch (_: UnsatisfiedLinkError) {
        false
    } catch (_: IOException) {
        false
    }

    /** The schema files ship in the APK; the library reads them from a folder, so copy them out (again if the app version changed). */
    private fun copySchemaFiles(destination: File) {
        val marker = File(destination, ".version")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).let { "${it.lastUpdateTime}" }
        if (marker.exists() && marker.readText() == version) return
        for (name in context.assets.list("rime/shared").orEmpty()) {
            context.assets.open("rime/shared/$name").use { input -> File(destination, name).outputStream().use { input.copyTo(it) } }
        }
        marker.writeText(version)
    }

    private fun read(): Pair<String, Composition> {
        val snapshot = RimeJni.snapshot(session)
        val committed = snapshot.getOrElse(0) { "" }
        val typed = snapshot.getOrElse(1) { "" }
        val shown = snapshot.getOrElse(2) { typed }
        return committed to Composition(preedit = shown, candidates = snapshot.drop(3), typed = typed)
    }

    private fun key(code: Int): Composition {
        if (!available || session == 0L) return Composition()
        RimeJni.processKey(session, code, 0)
        return read().second
    }

    override fun append(input: String): Composition {
        val ch = input.singleOrNull()?.takeIf { it.code in 33..126 } ?: return key(0)
        return key(ch.code)
    }

    override fun deleteLast(): Composition = key(KEY_BACKSPACE)

    override fun choose(index: Int): Choice {
        if (!available || !RimeJni.selectCandidate(session, index)) return Choice("")
        val (committed, composition) = read()
        return Choice(committed, composition)
    }

    override fun reset() {
        if (available) RimeJni.clear(session)
    }

    /** Ends this engine's session. */
    fun close() {
        if (session != 0L) RimeJni.destroySession(session)
        session = 0
    }

    companion object {
        /** Pinyin typed on a QWERTY keyboard. */
        const val SCHEMA_PINYIN = "pinyin_simp"

        /** The five basic strokes. */
        const val SCHEMA_STROKE = "stroke_simp"

        /** Bopomofo (Zhuyin) keys. */
        const val SCHEMA_ZHUYIN = "zhuyin_simp"

        private const val KEY_BACKSPACE = 0xff08
    }
}
