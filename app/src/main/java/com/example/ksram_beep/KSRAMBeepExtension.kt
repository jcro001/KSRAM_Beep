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
    private var lastSavedDevices: List<SavedDevices.SavedDevice> = emptyList()
    private var lastSourceId: String? = null

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
                lastSavedDevices = event.devices
                detectDrivetrainBrand()
            }
        )
    }

    private fun detectDrivetrainBrand() {
        val shiftingDevices = lastSavedDevices.filter { it.enabled && it.supportedDataTypes.contains(DataType.Type.SHIFTING_GEARS) }
        val sharedPreferences = getSharedPreferences("ksram_beep_prefs", Context.MODE_PRIVATE)

        val activeDevice = shiftingDevices.find { it.id == lastSourceId }
        
        // Prioritize hardware over extensions if we don't have a confirmed active source yet
        val hardwareDevices = shiftingDevices.filter { it.connectionType != "EXTENSION" }
        val primaryDevice = activeDevice ?: hardwareDevices.firstOrNull() ?: shiftingDevices.firstOrNull()

        if (primaryDevice == null) {
            if (autoDetectedBrand != "None") {
                autoDetectedBrand = "None"
                sharedPreferences.edit()
                    .putString("detected_brand", "None")
                    .putString("detected_source_name", "")
                    .apply()
            }
            return
        }
        
        // Update max gears
        primaryDevice.gearInfo?.let { info ->
            if (lastFrontMax == -1 && info.maxFrontGears > 0) lastFrontMax = info.maxFrontGears
            if (lastRearMax == -1 && info.maxRearGears > 0) lastRearMax = info.maxRearGears
        }

        var detected = "Unknown"
        
        // BRAND DETECTION LOGIC
        // 1. Check for SRAM keywords
        val isSram = { device: SavedDevices.SavedDevice ->
            val name = device.name.lowercase()
            val manufacturer = device.details.manufacturer?.lowercase() ?: ""
            val components = device.components?.values?.mapNotNull { it.manufacturer?.lowercase() } ?: emptyList()
            name.contains("sram") || name.contains("axs") || name.contains("etap") || 
            manufacturer.contains("sram") || components.any { it.contains("sram") }
        }

        // 2. Check for Shimano keywords (excluding 'ki2' as it's a virtual bridge)
        val isShimano = { device: SavedDevices.SavedDevice ->
            val name = device.name.lowercase()
            val manufacturer = device.details.manufacturer?.lowercase() ?: ""
            val components = device.components?.values?.mapNotNull { it.manufacturer?.lowercase() } ?: emptyList()
            (name.contains("shimano") || name.contains("di2")) || 
            (manufacturer.contains("shimano") && !name.contains("ki2")) || 
            components.any { it.contains("shimano") }
        }

        // Apply detection with priority: Active Device > Any Hardware > Any Shifter
        if (activeDevice != null) {
            if (isSram(activeDevice)) detected = "SRAM"
            else if (isShimano(activeDevice)) detected = "Shimano"
        }
        
        if (detected == "Unknown") {
            val sramHardware = hardwareDevices.find { isSram(it) }
            if (sramHardware != null) detected = "SRAM"
            else {
                val shimanoHardware = hardwareDevices.find { isShimano(it) }
                if (shimanoHardware != null) detected = "Shimano"
            }
        }

        // Fallback for 2x systems (highly likely SRAM AXS if not identified as Shimano)
        if (detected == "Unknown" && primaryDevice.gearInfo?.maxFrontGears == 2) {
            detected = "SRAM"
        }

        if (detected != autoDetectedBrand) {
            autoDetectedBrand = detected
            if (BuildConfig.DEBUG) Log.d("KSRAMBeep", "Auto-detected brand: $detected (Source: ${primaryDevice.name})")
            sharedPreferences.edit()
                .putString("detected_brand", detected)
                .putString("detected_source_name", primaryDevice.name)
                .apply()
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
                    handleGearUpdate(state.dataPoint.values, state.dataPoint.sourceId)
                }
            }
        )
    }

    private fun handleGearUpdate(values: Map<String, Double>, sourceId: String?) {
        if (sourceId != null && sourceId != lastSourceId) {
            lastSourceId = sourceId
            detectDrivetrainBrand()
        }
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
