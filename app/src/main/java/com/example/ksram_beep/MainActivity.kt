package com.example.ksram_beep

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ksram_beep.ui.theme.KSRAM_BeepTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KSRAM_BeepTheme {
                KSRAMBeepScreen()
            }
        }
    }
}

@Composable
fun KSRAMBeepScreen() {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("ksram_beep_prefs", Context.MODE_PRIVATE) }
    
    var lowGearAlertEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean("low_gear_alert_enabled", true))
    }
    var highGearAlertEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean("high_gear_alert_enabled", true))
    }
    var cassetteSize by remember {
        mutableIntStateOf(sharedPreferences.getInt("pref_cassette_size", 0))
    }
    var minBeepRetryDelay by remember {
        mutableIntStateOf(sharedPreferences.getInt("pref_min_beep_retry_delay", 5))
    }
    var drivetrainBrand by remember {
        mutableStateOf(sharedPreferences.getString("pref_drivetrain_brand", "Auto") ?: "Auto")
    }
    var autoDetectedBrand by remember {
        mutableStateOf(sharedPreferences.getString("detected_brand", "None") ?: "None")
    }
    var detectedSourceName by remember {
        mutableStateOf(sharedPreferences.getString("detected_source_name", "") ?: "")
    }

    LaunchedEffect(Unit) {
        if (BuildConfig.DEBUG) Log.d("KSRAMBeep", "MainActivity UI started. Initial brand: $autoDetectedBrand")
        while (true) {
            delay(2000)
            val prefBrand = sharedPreferences.getString("detected_brand", "None") ?: "None"
            val prefSource = sharedPreferences.getString("detected_source_name", "") ?: ""
            if (prefBrand != autoDetectedBrand) {
                if (BuildConfig.DEBUG) Log.d("KSRAMBeep", "UI Polling: Brand changed $autoDetectedBrand -> $prefBrand")
                autoDetectedBrand = prefBrand
            }
            if (prefSource != detectedSourceName) {
                detectedSourceName = prefSource
            }
        }
    }

    val listener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (BuildConfig.DEBUG) Log.d("KSRAMBeep", "Pref change detected: $key")
            when (key) {
                "detected_brand" -> {
                    autoDetectedBrand = prefs.getString(key, "None") ?: "None"
                }
                "detected_source_name" -> {
                    detectedSourceName = prefs.getString(key, "") ?: ""
                }
                "pref_drivetrain_brand" -> {
                    drivetrainBrand = prefs.getString(key, "Auto") ?: "Auto"
                }
                "pref_cassette_size" -> {
                    cassetteSize = prefs.getInt(key, 0)
                }
                "pref_min_beep_retry_delay" -> {
                    minBeepRetryDelay = prefs.getInt(key, 5)
                }
                "low_gear_alert_enabled" -> {
                    lowGearAlertEnabled = prefs.getBoolean(key, true)
                }
                "high_gear_alert_enabled" -> {
                    highGearAlertEnabled = prefs.getBoolean(key, true)
                }
            }
        }
    }

    DisposableEffect(sharedPreferences) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val isConnected = autoDetectedBrand != "None" && autoDetectedBrand != "Unknown"
    val displayStatus = if (!isConnected) {
        "Not Connected"
    } else {
        val brand = if (drivetrainBrand == "Auto") autoDetectedBrand else drivetrainBrand
        "Connected ($brand)"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 24.dp, end = 24.dp, bottom = 16.dp)
            ) {
                Text(
                    text = "KSRAM Beep Config",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                title = "Drivetrain Status",
                status = displayStatus,
                icon = if (isConnected) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.DirectionsBike,
                isActive = isConnected
            )

            if (isConnected && detectedSourceName.isNotEmpty()) {
                MiniInfoCard(title = "Detected Source", value = detectedSourceName)
            }

            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )

            CompactToggleCard(
                title = "Low Gear Alert",
                icon = Icons.Rounded.GraphicEq,
                checked = lowGearAlertEnabled,
                onCheckedChange = { isChecked ->
                    lowGearAlertEnabled = isChecked
                    sharedPreferences.edit().putBoolean("low_gear_alert_enabled", isChecked).apply()
                }
            )

            CompactToggleCard(
                title = "High Gear Alert",
                icon = Icons.Rounded.AudioFile,
                checked = highGearAlertEnabled,
                onCheckedChange = { isChecked ->
                    highGearAlertEnabled = isChecked
                    sharedPreferences.edit().putBoolean("high_gear_alert_enabled", isChecked).apply()
                }
            )

            DropdownSettingCard(
                title = "Cassette Size",
                icon = Icons.Rounded.FormatListNumbered,
                selectedValue = when(cassetteSize) {
                    0 -> "Auto"
                    else -> "${cassetteSize}S"
                },
                options = listOf(0 to "Auto", 10 to "10S", 11 to "11S", 12 to "12S", 13 to "13S"),
                onOptionSelected = { size ->
                    cassetteSize = size
                    sharedPreferences.edit().putInt("pref_cassette_size", size).apply()
                }
            )

            DropdownSettingCard(
                title = "Min Retry Delay",
                icon = Icons.Rounded.Settings,
                selectedValue = "${minBeepRetryDelay}s",
                options = listOf(1 to "1s", 5 to "5s", 10 to "10s", 15 to "15s", 30 to "30s"),
                onOptionSelected = { delay ->
                    minBeepRetryDelay = delay
                    sharedPreferences.edit().putInt("pref_min_beep_retry_delay", delay).apply()
                }
            )

            DropdownSettingCard(
                title = "Drivetrain",
                icon = Icons.Rounded.Settings,
                selectedValue = drivetrainBrand,
                options = listOf("Auto" to "Auto", "SRAM" to "SRAM", "Shimano" to "Shimano"),
                onOptionSelected = { brand ->
                    drivetrainBrand = brand
                    sharedPreferences.edit().putString("pref_drivetrain_brand", brand).apply()
                }
            )

            Text(
                text = "Extension Info",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "• Drivetrain Mode: Filters out front shift compensations and adjusts automated cross-chain limits based on paired hardware.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Compensation Shift Muting: Automatically running to suppress transient audio alerts during front ring operations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun <T> DropdownSettingCard(
    title: String,
    icon: ImageVector,
    selectedValue: String,
    options: List<Pair<T, String>>,
    onOptionSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Box {
                OutlinedCard(
                    onClick = { expanded = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedValue,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).padding(start = 4.dp)
                        )
                    }
                }
                
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onOptionSelected(value)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactToggleCard(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.8f)
            )
        }
    }
}

@Composable
fun MiniInfoCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StatusCard(title: String, status: String, icon: ImageVector, isActive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = title, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun InfoCard(title: String, description: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun DefaultPreview() {
    KSRAM_BeepTheme {
        KSRAMBeepScreen()
    }
}
