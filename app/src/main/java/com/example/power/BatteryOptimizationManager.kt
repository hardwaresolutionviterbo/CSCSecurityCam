package com.example.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BatteryInfo(
    val percentage: Int = 100,
    val isCharging: Boolean = false,
    val temperatureCelsius: Float = 25.0f,
    val isLowBattery: Boolean = false
)

class BatteryOptimizationManager(
    private val context: Context,
    private val onLowBatteryAlert: ((Int) -> Unit)? = null
) {
    private val _batteryInfo = MutableStateFlow(BatteryInfo())
    val batteryInfo: StateFlow<BatteryInfo> = _batteryInfo.asStateFlow()

    private var hasAlertedLowBattery = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)

                val pct = if (level >= 0 && scale > 0) ((level * 100) / scale) else 100
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                               status == BatteryManager.BATTERY_STATUS_FULL
                val tempC = temp / 10.0f
                val isLow = pct <= 20 && !charging

                _batteryInfo.value = BatteryInfo(
                    percentage = pct,
                    isCharging = charging,
                    temperatureCelsius = tempC,
                    isLowBattery = isLow
                )

                if (isLow && !hasAlertedLowBattery) {
                    hasAlertedLowBattery = true
                    onLowBatteryAlert?.invoke(pct)
                } else if (charging || pct > 25) {
                    hasAlertedLowBattery = false
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
    }

    fun stop() {
        try {
            context.unregisterReceiver(receiver)
        } catch (ignored: Exception) {}
    }
}
