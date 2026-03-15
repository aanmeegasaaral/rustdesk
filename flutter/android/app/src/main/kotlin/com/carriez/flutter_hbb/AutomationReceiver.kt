package com.carriez.flutter_hbb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

const val ACTION_START_HOST = "com.carriez.flutter_hbb.action.START_HOST"
const val ACTION_STOP_HOST = "com.carriez.flutter_hbb.action.STOP_HOST"
const val ACTION_OPEN_PERMISSIONS = "com.carriez.flutter_hbb.action.OPEN_PERMISSIONS"

// Optional shared secret for MacroDroid broadcasts
private const val EXTRA_TOKEN = "token"
private const val EXPECTED_TOKEN = "as_rustdesk_secret_2026"

class AutomationReceiver : BroadcastReceiver() {
    private val logTag = "AutomationReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(logTag, "onReceive action=${intent.action}")

        val token = intent.getStringExtra(EXTRA_TOKEN)
        if (token != EXPECTED_TOKEN) {
            Log.w(logTag, "Ignoring broadcast: invalid token")
            return
        }

        when (intent.action) {
            ACTION_START_HOST -> startRustDeskHost(context)
            ACTION_STOP_HOST -> stopRustDeskHost(context)
            ACTION_OPEN_PERMISSIONS -> openPermissionScreens(context)
        }
    }

    private fun startRustDeskHost(context: Context) {
        // This mirrors RustDesk's current service bootstrap path.
        val serviceIntent = Intent(context, MainService::class.java).apply {
            action = ACT_INIT_MEDIA_PROJECTION_AND_SERVICE
            putExtra(EXT_INIT_FROM_BOOT, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun stopRustDeskHost(context: Context) {
        val stopIntent = Intent(context, MainService::class.java).apply {
            action = ACTION_AUTOMATION_STOP_IN_SERVICE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(stopIntent)
        } else {
            context.startService(stopIntent)
        }
    }

    private fun openPermissionScreens(context: Context) {
        // Accessibility / Input control
        val accIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(accIntent)
    }
}
