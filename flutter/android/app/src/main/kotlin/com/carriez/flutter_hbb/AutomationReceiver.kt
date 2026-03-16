package com.carriez.flutter_hbb

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import ffi.FFI
import io.flutter.embedding.android.FlutterActivity

const val ACTION_AUTOMATION_START =
    "com.carriez.flutter_hbb.action.START_RUSTDESK_SERVICE"
const val ACTION_AUTOMATION_STOP =
    "com.carriez.flutter_hbb.action.STOP_RUSTDESK_SERVICE"
const val ACTION_AUTOMATION_GET_ID =
    "com.carriez.flutter_hbb.action.GET_RUSTDESK_ID"

const val ACTION_AUTOMATION_GET_ID_RESULT =
    "com.carriez.flutter_hbb.action.GET_RUSTDESK_ID_RESULT"

const val EXTRA_AUTOMATION_TOKEN = "token"
const val EXTRA_AUTOMATION_OK = "ok"
const val EXTRA_AUTOMATION_ID = "rustdesk_id"
const val EXTRA_AUTOMATION_ERROR = "error"

// Change this
private const val EXPECTED_AUTOMATION_TOKEN = "change_this_secret"

class AutomationReceiver : BroadcastReceiver() {
    private val logTag = "AutomationReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val token = intent.getStringExtra(EXTRA_AUTOMATION_TOKEN)

        if (EXPECTED_AUTOMATION_TOKEN.isNotEmpty() && token != EXPECTED_AUTOMATION_TOKEN) {
            Log.w(logTag, "Ignoring automation broadcast: invalid token")
            return
        }

        when (intent.action) {
            ACTION_AUTOMATION_START -> {
                startRustDesk(appContext)
            }

            ACTION_AUTOMATION_STOP -> {
                val pending = goAsync()
                stopRustDesk(appContext, pending)
            }

            ACTION_AUTOMATION_GET_ID -> {
                sendRustDeskId(appContext)
            }
        }
    }

    private fun startRustDesk(context: Context) {
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

    /**
     * Important:
     * We do NOT start the service just to stop it.
     * We try to bind to the already-running service, then call destroy(),
     * which matches RustDesk's own Flutter stop path.
     */
    private fun stopRustDesk(context: Context, pending: PendingResult) {
        val serviceIntent = Intent(context, MainService::class.java)

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                try {
                    val binder = service as? MainService.LocalBinder
                    val mainService = binder?.getService()

                    if (mainService != null) {
                        mainService.destroy()
                    }

                    // Stop the Rust core "service" state as well.
                    FFI.stopService()
                } catch (t: Throwable) {
                    Log.e(logTag, "Failed while stopping RustDesk", t)
                } finally {
                    try {
                        context.unbindService(this)
                    } catch (_: Throwable) {
                    }
                    pending.finish()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                pending.finish()
            }
        }

        val bound = try {
            context.bindService(serviceIntent, conn, 0)
        } catch (t: Throwable) {
            Log.e(logTag, "bindService failed for stop", t)
            false
        }

        if (!bound) {
            try {
                // If Android service is not running, still stop Rust core side.
                FFI.stopService()
                context.stopService(serviceIntent)
            } catch (t: Throwable) {
                Log.e(logTag, "Fallback stop failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private fun sendRustDeskId(context: Context) {
        try {
            ensureCoreInitialized(context)

            val id = FFI.getMyId()

            val resultIntent = Intent(ACTION_AUTOMATION_GET_ID_RESULT).apply {
                putExtra(EXTRA_AUTOMATION_OK, true)
                putExtra(EXTRA_AUTOMATION_ID, id)
            }

            context.sendBroadcast(resultIntent)
        } catch (t: Throwable) {
            Log.e(logTag, "Failed to get RustDesk ID", t)

            val resultIntent = Intent(ACTION_AUTOMATION_GET_ID_RESULT).apply {
                putExtra(EXTRA_AUTOMATION_OK, false)
                putExtra(EXTRA_AUTOMATION_ERROR, t.message ?: "Unknown error")
            }

            context.sendBroadcast(resultIntent)
        }
    }

    /**
     * Mirrors MainService startup enough for FFI ID reads to work even when the
     * Android foreground service is not already running.
     */
    private fun ensureCoreInitialized(context: Context) {
        FFI.init(context)

        val prefs = context.getSharedPreferences(
            KEY_SHARED_PREFERENCES,
            FlutterActivity.MODE_PRIVATE
        )
        val configPath = prefs.getString(KEY_APP_DIR_CONFIG_PATH, "") ?: ""

        FFI.startServer(configPath, "")
    }
}
