package com.alterlingua.app.keyboard.engine

/** Native entry points of librime (through librimejni.so, source in native/rime). Use through [RimeEngine]. */
internal object RimeJni {
    @JvmStatic external fun start(sharedDir: String, userDir: String, schemaId: String): Boolean
    @JvmStatic external fun createSession(schemaId: String): Long
    @JvmStatic external fun destroySession(session: Long)
    @JvmStatic external fun processKey(session: Long, keycode: Int, mask: Int): Boolean
    @JvmStatic external fun clear(session: Long)
    @JvmStatic external fun selectCandidate(session: Long, index: Int): Boolean

    /** [committed text, raw typed input, text to show for it, candidate 0, candidate 1, ...]; the committed text is returned once. */
    @JvmStatic external fun snapshot(session: Long): Array<String>
}
