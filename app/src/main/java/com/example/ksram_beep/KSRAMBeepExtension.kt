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
    private var lastEffectiveRearMax: Int = -1

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(this)
        karooSystem.connect { connected ->
            if (connected) {
                if (BuildConfig.DEBUG) Log.d("KSRAMBeep", "Connected to Karoo System")
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
        val manualCassetteSize = sharedPreferences.getInt("pref_cassette_size", 0)

        val frontGear = values[DataType.Field.SHIFTING_FRONT_GEAR]?.toInt() ?: -1
        val frontMax = values[DataType.Field.SHIFTING_FRONT_GEAR_MAX]?.toInt() ?: -1
        val rearGear = values[DataType.Field.SHIFTING_REAR_GEAR]?.toInt() ?: -1
        val sdkRearMax = values[DataType.Field.SHIFTING_REAR_GEAR_MAX]?.toInt() ?: -1

        // 1. Calculate effectiveRearMax including manual override and SRAM Cross-Chain protection
        val baseMax = if (manualCassetteSize > 0) manualCassetteSize else sdkRearMax
        var effectiveRearMax = baseMax
        var crossChainApplied = false
        if (frontMax > 1 && frontGear == 1 && baseMax > 1) {
            effectiveRearMax = baseMax - 1
            crossChainApplied = true
        }

        if (effectiveRearMax <= 0 || rearGear <= 0) {
            if (BuildConfig.DEBUG) Log.d("KSRAMBeep", "Invalid gear data: effectiveMax=$effectiveRearMax, rearGear=$rearGear")
            return
        }

        // 2. Determine state changes
        val frontChanged = lastFrontGearIndex != -1 && frontGear != lastFrontGearIndex
        val rearChanged = lastRearGearIndex != -1 && rearGear != lastRearGearIndex
        val limitChanged = lastEffectiveRearMax != -1 && effectiveRearMax != lastEffectiveRearMax

        if (BuildConfig.DEBUG) {
            Log.d("KSRAMBeep", "Update - Front: $frontGear/$frontMax, Rear: $rearGear, Max: $effectiveRearMax (SDK: $sdkRearMax, CrossChain: $crossChainApplied), Changes: [F:$frontChanged, R:$rearChanged, L:$limitChanged]")
        }

        // 3. Trigger Logic
        if (!frontChanged) {
            // Check Low Gear (Cog 1)
            if (rearGear == 1 && (rearChanged || limitChanged)) {
                if (BuildConfig.DEBUG) Log.d("KSRAMBeep", "Trigger: Reached low gear limit (1). Enabled: $lowGearAlertEnabled")
                if (lowGearAlertEnabled) playBeep(3000)
            } 
            // Check High Gear (Cog effectiveRearMax)
            else if (rearGear == effectiveRearMax && (rearChanged || limitChanged)) {
                if (BuildConfig.DEBUG) Log.d("KSRAMBeep", "Trigger: Reached high gear limit ($effectiveRearMax). Enabled: $highGearAlertEnabled")
                if (highGearAlertEnabled) playBeep(3800)
            }
        } else {
            if (BuildConfig.DEBUG) Log.d("KSRAMBeep", "Suppressed: Front shift detected (Compensation/Direct Front Shift)")
        }

        // 4. Update State Trackers
        lastRearGearIndex = rearGear
        lastFrontGearIndex = frontGear
        lastEffectiveRearMax = effectiveRearMax
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
