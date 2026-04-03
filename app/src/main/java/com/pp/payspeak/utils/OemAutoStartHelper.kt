package com.pp.payspeak.utils

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Detects OEM devices that aggressively kill background services (MIUI, Samsung, Huawei,
 * Oppo, Vivo, OnePlus, Realme) and provides deep links to their autostart/whitelist settings.
 *
 * Feasibility note: This card should only be shown on devices where OEM restrictions actually
 * exist. On stock Android devices, showing this UI would open a dead settings screen and
 * confuse the user. [isRequired] guards against this by checking the manufacturer.
 */
object OemAutoStartHelper {

    private val RESTRICTED_MANUFACTURERS = setOf(
        "xiaomi", "redmi", "poco",
        "samsung",
        "huawei", "honor",
        "oppo",
        "vivo",
        "oneplus",
        "realme"
    )

    /**
     * Returns true only on OEM devices that have proprietary autostart restrictions.
     * On stock Android (Google, Nokia, Motorola, Sony etc.) this returns false so the
     * UI card is never shown.
     */
    fun isRequired(): Boolean =
        Build.MANUFACTURER.lowercase() in RESTRICTED_MANUFACTURERS

    /**
     * Whether autostart appears to be granted.
     *  - MIUI: queries AppOps op 10008 (MIUI_OP_AUTO_START) — real API signal.
     *  - Other OEMs: no public API exists; reads a user-acknowledgement flag that is
     *    written when the user returns from the OEM settings screen.
     */
    fun isGranted(context: Context): Boolean {
        if (!isRequired()) return true
        return when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" -> isMiuiAutoStartEnabled(context)
            else -> isAcknowledged(context)
        }
    }

    /**
     * Call this after the user returns from the OEM settings for non-MIUI devices,
     * since there is no programmatic way to confirm the grant on Samsung/Huawei/etc.
     */
    fun acknowledge(context: Context) {
        prefs(context).edit().putBoolean(KEY_OEM_AUTOSTART_ACK, true).apply()
    }

    // ── Private ───────────────────────────────────────────────────────────

    private fun isAcknowledged(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OEM_AUTOSTART_ACK, false)

    /**
     * MIUI exposes autostart state via integer op 10008. There is no standard string name
     * for this op, so we must reflect into the hidden checkOpNoThrow(Int, Int, String) overload.
     * If reflection fails (different MIUI version, restricted policy), falls back to the same
     * user-acknowledgment approach used for other OEM devices.
     */
    private fun isMiuiAutoStartEnabled(context: Context): Boolean = try {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val method = AppOpsManager::class.java.getMethod(
            "checkOpNoThrow",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java
        )
        val result = method.invoke(ops, 10008, Process.myUid(), context.packageName) as Int
        result == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        // Reflection unavailable on this MIUI build — fall back to acknowledgment.
        isAcknowledged(context)
    }

    /**
     * Returns the best resolvable intent to open OEM-specific autostart/whitelist settings.
     * Tries manufacturer-specific deep links first, falls back to app details settings.
     * The fallback always resolves so this never returns null.
     */
    fun getAutoStartIntent(context: Context): Intent {
        val mfr = Build.MANUFACTURER.lowercase()
        val pm = context.packageManager

        val candidates: List<Intent> = when (mfr) {
            "xiaomi", "redmi", "poco" -> listOf(
                Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                    putExtra("extra_pkgname", context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            "samsung" -> listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                Intent().apply {
                    component = ComponentName(
                        "com.samsung.android.sm",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            "huawei", "honor" -> listOf(
                Intent("huawei.intent.action.HSM_BOOTAPP_MANAGER").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            "oppo", "realme" -> listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                Intent().apply {
                    component = ComponentName(
                        "com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            "vivo" -> listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            "oneplus" -> listOf(
                Intent().apply {
                    component = ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            else -> emptyList()
        }

        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return (candidates + fallback).firstOrNull { intent ->
            intent.component?.let { comp ->
                runCatching { pm.getActivityInfo(comp, 0); true }.getOrDefault(false)
            } ?: (intent.action != null && pm.queryIntentActivities(intent, 0).isNotEmpty())
        } ?: fallback
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("com.pp.payspeak.preferences", Context.MODE_PRIVATE)

    private const val KEY_OEM_AUTOSTART_ACK = "oem_autostart_ack"
}
