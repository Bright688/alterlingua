package com.alterlingua.app.share

import android.content.ContentResolver
import android.net.Uri
import java.io.InputStream

/** Reads the shared item through Android's ContentResolver, with the temporary permission the share granted. */
class ContentResolverAudioSource(private val resolver: ContentResolver) : AudioSource {
    override fun declaredType(address: String): String? = resolver.getType(Uri.parse(address))

    override fun open(address: String): InputStream? = resolver.openInputStream(Uri.parse(address))
}
