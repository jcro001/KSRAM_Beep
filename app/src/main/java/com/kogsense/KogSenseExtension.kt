package com.kogsense

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.SavedDevices
import io.hammerhead.karooext.models.StreamState

enum class DrivetrainBrand(val nameStr: String) {
    AUTO("Auto"),
    SRAM("SRAM"),
    SHIMANO("Shimano"),
    NONE("None"),
    UNKNOWN("Unknown");

    companion object {
        fun fromString(value: String?): DrivetrainBrand {
            return values().find { it.nameStr == value } ?: UNKNOWN
        }
    }
}

class KogSenseExtension : KarooExtension("kogsense", "1.0.0") {
    private lateinit var karooSystem: KarooSystemService
    private val stateLock = Any()

    private var gearConsumerId: String? = null
    private var rearCountConsumerId: String? = null
    private var deviceConsumerId: String? = null

    @Volatile private var lastFrontGearIndex: Int = -1
    @Volatile private var lastRearGearIndex: Int = -1
    @Volatile private var lastRearCount: Int = -1
    @Volatile private var lastFrontMax: Int = -1
    @Volatile private var lastRearMax: Int = -1
    @Volatile private var lastSavedDevices: List<SavedDevices.SavedDevice> = emptyList()
    @Volatile private var lastSourceId: String? = null
    @Volatile private var lastFrontShiftTimestamp: Long = 0
    @Volatile private var lastBeepTimestamp: Long = 0

    // Cached SharedPreferences values
    @Volatile private var lowGearAlertEnabled = true
    @Volatile private var highGearAlertEnabled = true
    @Volatile private var manualCassetteSize = 0
    @Volatile private var minBeepRetryDelay = 5
    @Volatile private var drivetrainBrandPref = DrivetrainBrand.AUTO
    
    @Volatile
    private var autoDetectedBrand = DrivetrainBrand.NONE
    @Volatile private var detectedSourceName = ""

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            KEY_LOW_GEAR_ALERT -> lowGearAlertEnabled = prefs.getBoolean(KEY_LOW_GEAR_ALERT, true)
            KEY_HIGH_GEAR_ALERT -> highGearAlertEnabled = prefs.getBoolean(KEY_HIGH_GEAR_ALERT, true)
            KEY_CASSETTE_SIZE -> manualCassetteSize = prefs.getInt(KEY_CASSETTE_SIZE, 0)
            KEY_MIN_BEEP_RETRY_DELAY -> minBeepRetryDelay = prefs.getInt(KEY_MIN_BEEP_RETRY_DELAY, 5)
            KEY_DRIVETRAIN_BRAND_PREF -> drivetrainBrandPref = DrivetrainBrand.fromString(prefs.getString(KEY_DRIVETRAIN_BRAND_PREF, DrivetrainBrand.AUTO.nameStr))
            KEY_DETECTED_BRAND -> autoDetectedBrand = DrivetrainBrand.fromString(prefs.getString(KEY_DETECTED_BRAND, DrivetrainBrand.NONE.nameStr))
            KEY_DETECTED_SOURCE_NAME -> detectedSourceName = prefs.getString(KEY_DETECTED_SOURCE_NAME, "") ?: ""
        }
    }

    override fun onCreate() {
        super.onCreate()
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        lowGearAlertEnabled = sharedPreferences.getBoolean(KEY_LOW_GEAR_ALERT, true)
        highGearAlertEnabled = sharedPreferences.getBoolean(KEY_HIGH_GEAR_ALERT, true)
        manualCassetteSize = sharedPreferences.getInt(KEY_CASSETTE_SIZE, 0)
        minBeepRetryDelay = sharedPreferences.getInt(KEY_MIN_BEEP_RETRY_DELAY, 5)
        drivetrainBrandPref = DrivetrainBrand.fromString(sharedPreferences.getString(KEY_DRIVETRAIN_BRAND_PREF, DrivetrainBrand.AUTO.nameStr))
        autoDetectedBrand = DrivetrainBrand.fromString(sharedPreferences.getString(KEY_DETECTED_BRAND, DrivetrainBrand.NONE.nameStr))
        detectedSourceName = sharedPreferences.getString(KEY_DETECTED_SOURCE_NAME, "") ?: ""
        
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefsListener)

        karooSystem = KarooSystemService(this)
        karooSystem.connect { connected ->
            if (connected) {
                if (BuildConfig.DEBUG) Log.d("KogSense", "Connected to Karoo System")
                subscribeToGears()
                subscribeToDevices()
            }
        }
    }

    private fun subscribeToDevices() {
        if (BuildConfig.DEBUG) Log.d("KogSense", "Subscribing to devices...")
        deviceConsumerId?.let { karooSystem.removeConsumer(it) }
        deviceConsumerId = karooSystem.addConsumer<SavedDevices>(
            onEvent = { event ->
                if (BuildConfig.DEBUG) Log.d("KogSense", "Received SavedDevices event: ${event.devices.size} devices")
                synchronized(stateLock) {
                    lastSavedDevices = event.devices
                    detectDrivetrainBrandLocked()
                }
            }
        )
    }

    private fun detectDrivetrainBrand() = synchronized(stateLock) {
        detectDrivetrainBrandLocked()
    }

    private fun detectDrivetrainBrandLocked() {
        val shiftingDevices = lastSavedDevices.filter { device ->
            device.enabled && (
                device.supportedDataTypes.contains(DataType.Type.SHIFTING_GEARS) ||
                device.supportedDataTypes.contains(DataType.Type.SHIFTING_FRONT_GEAR) ||
                device.supportedDataTypes.contains(DataType.Type.SHIFTING_REAR_GEAR)
            )
        }
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Find the active device in all saved devices
        if (BuildConfig.DEBUG) Log.d("KogSense", "Detecting brand. Active Source ID: $lastSourceId, Total Saved: ${lastSavedDevices.size}")
        
        val activeDevice = lastSourceId?.let { id ->
            val match = lastSavedDevices.find { it.id.equals(id, ignoreCase = true) }
            if (match == null) {
                // Try fuzzy match for extension sources which might have a prefix
                lastSavedDevices.find { id.contains(it.id, ignoreCase = true) || it.id.contains(id, ignoreCase = true) }
            } else match
        }
        
        // Priority: 1. Active Device, 2. (ONLY if no active source) First Hardware, 3. First available shifting device
        val primaryDevice = if (lastSourceId != null) {
            activeDevice
        } else {
            shiftingDevices.find { it.connectionType != "EXTENSION" } ?: shiftingDevices.firstOrNull()
        }

        var detected = DrivetrainBrand.UNKNOWN
        val deviceName = primaryDevice?.name ?: "Unknown Source"

        if (primaryDevice != null) {
            // BRAND DETECTION LOGIC
            val isSram = { device: SavedDevices.SavedDevice ->
                val name = device.name.lowercase()
                val manufacturer = device.details.manufacturer?.lowercase() ?: ""
                name.contains("sram") || name.contains("axs") || name.contains("etap") || 
                manufacturer.contains("sram") || device.id.lowercase().contains("sram")
            }

            val isShimano = { device: SavedDevices.SavedDevice ->
                val name = device.name.lowercase()
                val manufacturer = device.details.manufacturer?.lowercase() ?: ""
                val id = device.id.lowercase()
                (device.connectionType == "EXTENSION" && (name.contains("ki2") || name.contains("di2") || name.contains("shimano"))) || 
                name.contains("shimano") || name.contains("di2") || name.contains("ki2") ||
                name.contains("dura-ace") || name.contains("ultegra") || name.contains("grx") || 
                name.contains("105") || name.contains("r9150") || name.contains("r8050") || 
                name.contains("r7150") || name.contains("r8150") || name.contains("r9250") ||
                name.contains("r9170") || name.contains("r8070") || name.contains("r7170") ||
                name.contains("r8170") || name.contains("r9270") ||
                name.contains("r9100") || name.contains("r8000") || name.contains("r7000") ||
                name.contains("r9200") || name.contains("r8100") || name.contains("r7100") ||
                name.contains("st-r") || name.contains("fd-r") || name.contains("rd-r") ||
                name.contains("ew-wu") || name.contains("ew-en") || name.contains("d-fly") ||
                name.contains("dura ace") ||
                manufacturer.contains("shimano") || id.contains("ki2") || id.contains("shimano")
            }

            if (isSram(primaryDevice)) {
                detected = DrivetrainBrand.SRAM
                // Default defaults for SRAM (most common AXS is 12s/2x)
                if (lastRearMax <= 0) lastRearMax = 12
                if (lastFrontMax <= 0) lastFrontMax = 2
            } else if (isShimano(primaryDevice)) {
                detected = DrivetrainBrand.SHIMANO
                // Default defaults for Shimano (most common Di2 is 12s/2x)
                if (lastRearMax <= 0) lastRearMax = 12
                if (lastFrontMax <= 0) lastFrontMax = 2
            }

            // Update max gears from device info if available (overwrites defaults)
            primaryDevice.gearInfo?.let { info ->
                if (info.maxFrontGears > 0) lastFrontMax = info.maxFrontGears
                if (info.maxRearGears > 0) lastRearMax = info.maxRearGears
            }
        } else if (lastSourceId != null) {
            // FALLBACK: If we have an active source but can't find it in SavedDevices yet (common for extensions),
            // check the Source ID string directly for hints.
            val id = lastSourceId!!.lowercase()
            if (id.contains("ki2") || id.contains("di2") || id.contains("shimano")) {
                detected = DrivetrainBrand.SHIMANO
            } else if (id.contains("sram") || id.contains("axs")) {
                detected = DrivetrainBrand.SRAM
            }
        } else if (autoDetectedBrand != DrivetrainBrand.NONE) {
            // No source and no devices found, reset.
            autoDetectedBrand = DrivetrainBrand.NONE
            detectedSourceName = ""
            sharedPreferences.edit()
                .putString(KEY_DETECTED_BRAND, DrivetrainBrand.NONE.nameStr)
                .putString(KEY_DETECTED_SOURCE_NAME, "")
                .apply()
            return
        }

        if (detected != autoDetectedBrand || deviceName != detectedSourceName) {
            autoDetectedBrand = detected
            detectedSourceName = deviceName
            if (BuildConfig.DEBUG) Log.d("KogSense", "Brand detection updated: ${detected.nameStr} (Source: $deviceName, Active: ${lastSourceId != null})")
            sharedPreferences.edit()
                .putString(KEY_DETECTED_BRAND, detected.nameStr)
                .putString(KEY_DETECTED_SOURCE_NAME, deviceName)
                .apply()
        }
    }

    private fun subscribeToGears() {
        gearConsumerId?.let { karooSystem.removeConsumer(it) }
        rearCountConsumerId?.let { karooSystem.removeConsumer(it) }
        
        gearConsumerId = karooSystem.addConsumer<OnStreamState>(
            params = OnStreamState.StartStreaming(DataType.Type.SHIFTING_GEARS),
            onEvent = { event ->
                val state = event.state
                if (state is StreamState.Streaming) {
                    handleGearUpdate(state.dataPoint.dataTypeId, state.dataPoint.values, state.dataPoint.sourceId)
                }
            }
        )
        
        rearCountConsumerId = karooSystem.addConsumer<OnStreamState>(
            params = OnStreamState.StartStreaming(DataType.Type.SHIFTING_COUNT_REAR),
            onEvent = { event ->
                val state = event.state
                if (state is StreamState.Streaming) {
                    handleGearUpdate(state.dataPoint.dataTypeId, state.dataPoint.values, state.dataPoint.sourceId)
                }
            }
        )
    }

    private fun handleGearUpdate(dataTypeId: String, values: Map<String, Double>, sourceId: String?) {
        var beepFreq: Int? = null
        
        synchronized(stateLock) {
            if (sourceId != null && sourceId != lastSourceId) {
                lastSourceId = sourceId
                detectDrivetrainBrand()
            }

            var drivetrainBrand = if (drivetrainBrandPref == DrivetrainBrand.AUTO) autoDetectedBrand else drivetrainBrandPref

            val frontGear = values[DataType.Field.SHIFTING_FRONT_GEAR]?.toInt() ?: lastFrontGearIndex
            val frontMax = values[DataType.Field.SHIFTING_FRONT_GEAR_MAX]?.toInt()?.also { lastFrontMax = it } ?: lastFrontMax
            val rearGear = values[DataType.Field.SHIFTING_REAR_GEAR]?.toInt() ?: lastRearGearIndex
            val sdkRearMax = values[DataType.Field.SHIFTING_REAR_GEAR_MAX]?.toInt()?.also { lastRearMax = it } ?: lastRearMax
            
            // Confirmed against SDK: SHIFTING_COUNT_REAR type uses SHIFTING_COUNT field
            val rearCount = if (dataTypeId == DataType.Type.SHIFTING_COUNT_REAR) {
                values[DataType.Field.SHIFTING_COUNT]?.toInt() ?: lastRearCount
            } else {
                lastRearCount
            }

            val now = System.currentTimeMillis()
            val frontChanged = frontGear != lastFrontGearIndex && lastFrontGearIndex != -1
            if (frontChanged) {
                lastFrontShiftTimestamp = now
            }
            lastFrontGearIndex = frontGear

            val baseMax = if (manualCassetteSize > 0) manualCassetteSize else sdkRearMax
            if (rearGear <= 0) return

            // BEHAVIORAL DETECTION:
            // Notice which system it is based on gear behavior.
            // 1. If we reach a gear that is blocked on SRAM AXS 2x (e.g. 1/12), we are Shimano.
            val blockedGear = when (baseMax) {
                12 -> 11
                11 -> 10
                else -> -1
            }
            
            var brandSwitchedThisUpdate = false
            // Only trigger behavioral detection if we have enough info and are in Auto mode
            val definitelyNotSramBlocked = baseMax > 0 && frontMax > 1 && frontGear == 1 && 
                                          blockedGear > 0 && rearGear > blockedGear
            
            if (definitelyNotSramBlocked && autoDetectedBrand != DrivetrainBrand.SHIMANO && drivetrainBrandPref == DrivetrainBrand.AUTO) {
                if (BuildConfig.DEBUG) Log.d("KogSense", "Behavioral Detection: Reached gear $rearGear in small ring. Identifying as Shimano.")
                autoDetectedBrand = DrivetrainBrand.SHIMANO
                drivetrainBrand = DrivetrainBrand.SHIMANO
                val activeDeviceName = lastSavedDevices.find { it.id == lastSourceId }?.name ?: detectedSourceName
                detectedSourceName = activeDeviceName
                val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                sharedPreferences.edit()
                    .putString(KEY_DETECTED_BRAND, DrivetrainBrand.SHIMANO.nameStr)
                    .putString(KEY_DETECTED_SOURCE_NAME, activeDeviceName)
                    .apply()
                brandSwitchedThisUpdate = true
            }
            
            val rearChanged = rearGear != lastRearGearIndex && lastRearGearIndex != -1
            val shiftAttempted = rearCount != lastRearCount && lastRearCount != -1
            lastRearCount = rearCount

            if (BuildConfig.DEBUG && (rearChanged || frontChanged || shiftAttempted)) {
                Log.d("KogSense", "Update - F: $frontGear/$frontMax, R: $rearGear, Max: $baseMax, Brand: ${drivetrainBrand.nameStr}, FC: $frontChanged, RC: $rearChanged, SA: $shiftAttempted")
            }

            val isSram = drivetrainBrand == DrivetrainBrand.SRAM
            val isLowLimit = rearGear == 1
            
            // Generalized limit logic
            // SRAM Road 2x: typically blocks baseMax and baseMax - 1 in small ring
            val sramBlocked = isSram && frontMax > 1 && frontGear == 1 && baseMax > 1
            val isSramHighLimit = sramBlocked && (rearGear == baseMax - 1)
            
            val isStandardHighLimit = (baseMax > 0 && rearGear == baseMax)
            val isHighLimit = isSramHighLimit || isStandardHighLimit

            if (rearChanged || (shiftAttempted && (isLowLimit || isHighLimit))) {
                val isCompensationShift = frontChanged || (now - lastFrontShiftTimestamp < COMPENSATION_SHIFT_WINDOW_MS)
                if (isCompensationShift) {
                    if (BuildConfig.DEBUG) Log.d("KogSense", "Muting beep for compensation shift")
                } else if (brandSwitchedThisUpdate) {
                    if (BuildConfig.DEBUG) Log.d("KogSense", "Muting beep due to brand switch correction")
                } else {
                    val timeSinceLastBeep = now - lastBeepTimestamp
                    val retryDelayMs = minBeepRetryDelay * 1000L
                    
                    // Beep if it's a new gear shift to a limit, 
                    // OR if we are already at a limit and the user shifted again (detected via shift count) after the delay.
                    if (rearChanged || timeSinceLastBeep >= retryDelayMs) {
                        if (isLowLimit && lowGearAlertEnabled) {
                            if (BuildConfig.DEBUG) Log.d("KogSense", "Triggering Low Limit Beep. Proactive: $rearChanged")
                            beepFreq = LOW_LIMIT_BEEP_FREQUENCY_HZ
                            lastBeepTimestamp = now
                        } else if (isHighLimit && highGearAlertEnabled) {
                            if (BuildConfig.DEBUG) Log.d("KogSense", "Triggering High Limit Beep. Proactive: $rearChanged")
                            beepFreq = HIGH_LIMIT_BEEP_FREQUENCY_HZ
                            lastBeepTimestamp = now
                        }
                    }
                }
            }

            lastRearGearIndex = rearGear
        }
        
        // Dispatch IPC call outside the lock to avoid blocking other data stream events
        beepFreq?.let { playBeep(it) }
    }

    private fun playBeep(frequency: Int) {
        val tones = listOf(PlayBeepPattern.Tone(frequency, BEEP_DURATION_MS))
        karooSystem.dispatch(PlayBeepPattern(tones))
    }

    override fun onDestroy() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefsListener)
        gearConsumerId?.let { karooSystem.removeConsumer(it) }
        rearCountConsumerId?.let { karooSystem.removeConsumer(it) }
        deviceConsumerId?.let { karooSystem.removeConsumer(it) }
        karooSystem.disconnect()
        super.onDestroy()
    }

    companion object {
        private const val PREFS_NAME = "kogsense_prefs"
        private const val KEY_DETECTED_BRAND = "detected_brand"
        private const val KEY_DETECTED_SOURCE_NAME = "detected_source_name"
        private const val KEY_DRIVETRAIN_BRAND_PREF = "pref_drivetrain_brand"
        private const val KEY_LOW_GEAR_ALERT = "low_gear_alert_enabled"
        private const val KEY_HIGH_GEAR_ALERT = "high_gear_alert_enabled"
        private const val KEY_CASSETTE_SIZE = "pref_cassette_size"
        private const val KEY_MIN_BEEP_RETRY_DELAY = "pref_min_beep_retry_delay"

        private const val COMPENSATION_SHIFT_WINDOW_MS = 1000L
        private const val LOW_LIMIT_BEEP_FREQUENCY_HZ = 3000
        private const val HIGH_LIMIT_BEEP_FREQUENCY_HZ = 3800
        private const val BEEP_DURATION_MS = 200
    }
}
