package com.sandpause.app.lock

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import com.sandpause.app.admin.Permissions

/** Brings the lock screen to the front. Also used from the accessibility service. */
object LockLauncher {

    fun showLock(context: Context) {
        val intent = Intent(context, ChildLockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Not expected: the activity is declared in the manifest.
        }
    }

    fun toast(context: Context, @StringRes textRes: Int) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, textRes, Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * Calls. The lock screen can place only emergency calls and calls to the parent
 * contacts. Arbitrary numbers, USSD and mobile-money codes cannot be dialled.
 */
object CallLauncher {

    /** Places a call to [number]. Returns false if no app could handle it. */
    fun call(context: Context, number: String): Boolean {
        val uri = Uri.fromParts("tel", number, null)
        val base = if (Permissions.hasCallPermission(context)) {
            Intent(Intent.ACTION_CALL, uri)
        } else {
            Intent(Intent.ACTION_DIAL, uri)
        }
        val intent = base.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    /** Emergency numbers available in Uganda. */
    val EMERGENCY_NUMBERS: List<String> = listOf("112", "999")
}
