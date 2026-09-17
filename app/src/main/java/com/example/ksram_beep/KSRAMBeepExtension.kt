package com.example.ksram_beep

import android.content.Context
import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.SavedDevices
import io.hammerhead.karooext.models.StreamState

class KSRAMBeepExtension : KarooExtension("ksram-beep", "1.0.0") {
    private lateinit var karooSystem: KarooSystemService
    private var gearConsumerId: String? = null
    private var deviceConsumerId: String? = null

    private var lastFrontGearIndex: Int = -1
    private var lastRearGearIndex: Int = -1
    private var lastFrontMax: Int = -1
    private var lastRearMax: Int = -1

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(this)
        karooSystem.connect { connected ->
            if (connected) {
                if (BuildConfig.DEBUG) Log.d("KSRAMBeep", "Connected to Karoo System")
                subscribeToGears()
                subscribeToDevices()
            }
        }
    }

    private fun subscribeToDevices() {
        deviceConsumerId?.let { karooSystem.removeConsumer(it) }
        deviceConsumerId = karooSystem.addConsumer<SavedDevices>(
            onEvent = { event ->
                detectDrivetrainBrand(event.devices)
            }
        )
    }

    private fun detectDrivetrainBrand(devices: List<SavedDevices.SavedDevice>) {
        val shiftingDevices = devices.filter { it.enabled }
        if (shiftingDevices.isEmpty()) return

        val sharedPreferences = getSharedPreferences("ksram_beep_prefs", Context.MODE_PRIVATE)
        val currentBrand = sharedPreferences.getString("pref_drivetrain_brand", "Auto") ?: "Auto"
        
        // Update max gears from device info as fallback
        shiftingDevices.firstOrNull { it.gearInfo != null }?.gearInfo?.let { info ->
            if (lastFrontMax == -1 && info.maxFrontGears > 0) lastFrontMax = info.maxFrontGears
            if (lastRearMax == -1 && info.maxRearGears > 0) lastRearMax = info.maxRearGears
            if (BuildConfig.DEBUG) Log.d("KSRAMBeep", "Updated max gears from SavedDevices: F=${info.maxFrontGears}, R=${info.maxRearGears}")
        }

        var detected = "Auto"
        for (device in shiftingDevices) {
            val name = device.name.lowercase()
            val manufacturer = device.details.manufacturer?.lowercase() ?: ""
            val componentManufacturers = device.components?.values?.mapNotNull { it.manufacturer?.lowercase() } ?: emptyList()
            
            val isSram = name.contains("sram") || name.contains("axs") || name.contains("etap") || 
                         manufacturer.contains("sram") || componentManufacturers.any { it.contains("sram") }
            
            val isShimano = name.contains("shimano") || name.contains("di2") || name.contains("ki2") || 
                            device.connectionType == "EXTENSION" || manufacturer.contains("shimano") ||
                            componentManufacturers.any { it.contains("shimano") }

            if (isSram) {
                detected = "SRAM"
                break
            } else if (isShimano) {
                detected = "Shimano"
                break
            }
        }
        
        // Fallback: If it's a 2x system and not explicitly Shimano, assume SRAM for protection logic
        if (detected == "Auto") {
            val is2x = shiftingDevices.any { it.gearInfo?.maxFrontGears == 2 }
            if (is2x) detected = "SRAM"
        }

        if (detected != "Auto" && detected != autoDetectedBrand) {
            autoDetectedBrand = detected
            if (BuildConfig.DEBUG) Log.d("KSRAMBeep", "Auto-detected brand: $autoDetectedBrand")
            // Store it so MainActivity can show it
            sharedPreferences.edit().putString("detected_brand", detected).apply()
        }
    }

    @Volatile
    private var autoDetectedBrand: String = "Auto"

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
        val drivetrainBrandPref = sharedPreferences.getString("pref_drivetrain_brand", "Auto") ?: "Auto"
        val drivetrainBrand = if (drivetrainBrandPref == "Auto") autoDetectedBrand else drivetrainBrandPref

        // Persistent field updates: Karoo often omits MAX fields in periodic updates
        val frontGear = values[DataType.Field.SHIFTING_FRONT_GEAR]?.toInt() ?: lastFrontGearIndex
        val frontMax = values[DataType.Field.SHIFTING_FRONT_GEAR_MAX]?.toInt()?.also { lastFrontMax = it } ?: lastFrontMax
        val rearGear = values[DataType.Field.SHIFTING_REAR_GEAR]?.toInt() ?: lastRearGearIndex
        val sdkRearMax = values[DataType.Field.SHIFTING_REAR_GEAR_MAX]?.toInt()?.also { lastRearMax = it } ?: lastRearMax

        val baseMax = if (manualCassetteSize > 0) manualCassetteSize else sdkRearMax

        // If we still don't know the cassette size, we can't safely beep for the high limit
        if (rearGear <= 0) return
        
        val frontChanged = frontGear != lastFrontGearIndex
        val rearChanged = rearGear != lastRearGearIndex

        if (BuildConfig.DEBUG) {
            Log.d("KSRAMBeep", "Update - F: $frontGear/$frontMax, R: $rearGear, Max: $baseMax, Brand: $drivetrainBrand, lastR: $lastRearGearIndex")
        }

        val isSram = drivetrainBrand == "SRAM"
        val isShimano = drivetrainBrand == "Shimano"

        if (rearChanged && lastRearGearIndex != -1) {
            if (frontChanged && lastFrontGearIndex != -1) {
                if (BuildConfig.DEBUG) Log.d("KSRAMBeep", "Muting beep: Compensation shift detected")
            } else {
                val isLowLimit = rearGear == 1
                var isHighLimit = (baseMax > 0 && rearGear == baseMax)
                
                // SRAM AXS/eTap 2x specific: handle software-blocked gears in small ring
                if (isSram && frontMax > 1 && frontGear == 1) {
                    // 12-speed AXS blocks cogs 11 & 12 (limit is 10)
                    if (baseMax == 12 && rearGear == 10) {
                        isHighLimit = true
                    }
                    // 11-speed eTap blocks cog 11 (limit is 10)
                    else if (baseMax == 11 && rearGear == 10) {
                        isHighLimit = true
                    }
                }
                
                if (isLowLimit) {
                    if (BuildConfig.DEBUG) Log.d("KSRAMBeep", "Beep: Low Limit (Gear $rearGear)")
                    if (lowGearAlertEnabled) playBeep(3000)
                } else if (isHighLimit) {
                    if (BuildConfig.DEBUG) Log.d("KSRAMBeep", "Beep: High Limit (Gear $rearGear)")
                    if (highGearAlertEnabled) playBeep(3800)
                }
            }
        }

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
        deviceConsumerId?.let { karooSystem.removeConsumer(it) }
        karooSystem.disconnect()
        super.onDestroy()
    }
}
