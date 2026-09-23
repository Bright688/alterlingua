package com.alterlingua.app.keyboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import com.alterlingua.app.localization.LocalizedActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.alterlingua.app.AlterLinguaApplication
import com.alterlingua.app.setup.SystemSettings
import com.alterlingua.app.setup.openSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * An invisible screen that asks Android for the microphone permission for the keyboard (a keyboard cannot show the
 * question itself). The keyboard's voice panel has already explained why. It closes as soon as the question is answered,
 * and never nags: if Android will not show the question again, it opens AlterLingua's settings instead.
 */
class MicrophonePermissionActivity : LocalizedActivity() {

    private val request = registerForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }
        val settings = (application as AlterLinguaApplication).userSettings
        lifecycleScope.launch {
            val askedBefore = settings.settings.first().microphonePermissionAsked
            val canAsk = !askedBefore || ActivityCompat.shouldShowRequestPermissionRationale(this@MicrophonePermissionActivity, Manifest.permission.RECORD_AUDIO)
            if (canAsk) {
                settings.update { it.copy(microphonePermissionAsked = true) }
                request.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                // Declined for good: only AlterLingua's settings page can change it.
                openSettings(SystemSettings.appDetails(this@MicrophonePermissionActivity))
                finish()
            }
        }
    }
}
