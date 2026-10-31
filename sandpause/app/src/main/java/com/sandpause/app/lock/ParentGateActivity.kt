package com.sandpause.app.lock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import com.sandpause.app.security.PinManager
import com.sandpause.app.ui.common.ParentGateDialog
import com.sandpause.app.ui.theme.SandPauseTheme
import javax.inject.Inject

/**
 * A PIN prompt shown over another app. It is used before the Play Store opens
 * when "Require PIN for Play Store" is on. On success it closes and the app
 * underneath stays where it is.
 */
@AndroidEntryPoint
class ParentGateActivity : ComponentActivity() {

    @Inject lateinit var pinManager: PinManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SandPauseTheme {
                ParentGateDialog(
                    verify = { pinManager.verify(it) },
                    onVerified = { finish() },
                    onCancel = { finish() },
                )
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(
                Intent(context, ParentGateActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }
}
