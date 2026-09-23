package com.alterlingua.app.translation

import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One request in flight, so that it can be closed from another thread when the caller gives up. */
internal class HttpCall {
    private val connection = AtomicReference<HttpURLConnection?>()
    private val cancelled = AtomicBoolean(false)

    /** Registers the connection. Returns false if the call was already cancelled (the connection is then closed). */
    fun attach(new: HttpURLConnection): Boolean {
        connection.set(new)
        if (cancelled.get()) new.disconnect()
        return !cancelled.get()
    }

    fun cancel() {
        cancelled.set(true)
        connection.get()?.disconnect()
    }
}
