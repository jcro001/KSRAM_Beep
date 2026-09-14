package com.example.ksram_beep

import android.content.Context
import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.StreamState

class KSRAMBeepExtension : KarooExtension("ksram-beep", "1.0.0") {
    private lateinit var karooSystem: KarooSystemService
    private var gearConsumerId: String? = null

    private var lastFrontGearIndex: Int = -1
    private var lastRearGearIndex: Int = -1

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(this)
        karooSystem.connect { connected ->
            if (connected) {
                Log.d("KSRAMBeep", "Connected to Karoo System")
                subscribeToGears()
            }
        }
    }

    private fun subscribeToGears() {
        gearConsumerId?.let { karooSystem.removeConsumer(it) }

        gearConsumerId = karooSystem.addConsumer<OnStreamState>(
            params = OnStreamState.StartStreaming(DataType.Type.SHIFTING_GEARS),
            onEvent = { event ->
                val state = event.state
                if (state is StreamState.Streaming) {
                    handleGearUpdate(state.dataPoint.values)
                }
            }
        )
    }

    private fun handleGearUpdate(values: Map<String, Double>) {
        val sharedPreferences = getSharedPreferences("ksram_beep_prefs", Context.MODE_PRIVATE)
        val lowGearAlertEnabled = sharedPreferences.getBoolean("low_gear_alert_enabled", true)
        val highGearAlertEnabled = sharedPreferences.getBoolean("high_gear_alert_enabled", true)

        val frontGear = values[DataType.Field.SHIFTING_FRONT_GEAR]?.toInt() ?: -1
        val rearGear = values[DataType.Field.SHIFTING_REAR_GEAR]?.toInt() ?: -1
        val rearMax = values[DataType.Field.SHIFTING_REAR_GEAR_MAX]?.toInt() ?: -1

        Log.d("KSRAMBeep", "Update - Front: $frontGear, Rear: $rearGear, Max: $rearMax, LastFront: $lastFrontGearIndex, LastRear: $lastRearGearIndex")

        if (rearMax <= 0 || rearGear <= 0) {
            Log.d("KSRAMBeep", "Invalid gear data: rearMax=$rearMax, rearGear=$rearGear")
            return
        }

        // Track front shift activity to suppress compensation beeps
        val frontChanged = lastFrontGearIndex != -1 && frontGear != lastFrontGearIndex

        // Detect when the rear gear is hitting limits
        if (!frontChanged) {
            if (rearGear == 1 && lastRearGearIndex != 1) {
                Log.d("KSRAMBeep", "Trigger: Reached lowest gear (1). Enabled: $lowGearAlertEnabled")
                if (lowGearAlertEnabled) {
                    playBeep(3000) // Lower pitch beep
                }
            } else if (rearGear == rearMax && lastRearGearIndex != rearMax) {
                Log.d("KSRAMBeep", "Trigger: Reached highest gear ($rearMax). Enabled: $highGearAlertEnabled")
                if (highGearAlertEnabled) {
                    playBeep(3800) // Higher pitch beep
                }
            } else {
                Log.d("KSRAMBeep", "No limit reached or already at limit. Rear: $rearGear, Max: $rearMax")
            }
        } else {
            Log.d("KSRAMBeep", "Suppressed: Front changed from $lastFrontGearIndex to $frontGear (Compensation Shift)")
        }

        // Always save state positions
        lastRearGearIndex = rearGear
        lastFrontGearIndex = frontGear
    }

    private fun playBeep(frequency: Int) {
        val tones = listOf(
            PlayBeepPattern.Tone(frequency, 200)
        )
        karooSystem.dispatch(PlayBeepPattern(tones))
    }

    override fun onDestroy() {
        gearConsumerId?.let { karooSystem.removeConsumer(it) }
        karooSystem.disconnect()
        super.onDestroy()
    }
}
