package com.ariok12.virtualmic.ui.components


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ariok12.virtualmic.AppSettings
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onNavigateBack: () -> Unit,
    onMicGainChange: (Float) -> Unit,
    onStereoChange: (Boolean) -> Unit,
    onSampleRateChange: (Int) -> Unit,
    onThemeChange: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // App Section
            SettingsSection(title = "App") {
                ListItem(
                    headlineContent = { Text("Dark Theme") },
                    trailingContent = {
                        Switch(checked = settings.isDarkMode, onCheckedChange = onThemeChange)
                    }
                )
            }

            // Audio Format Section
            SettingsSection(title = "Audio Format") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Channels", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !settings.isStereo,
                            onClick = { onStereoChange(false) },
                            label = { Text("Mono") }
                        )
                        FilterChip(
                            selected = settings.isStereo,
                            onClick = { onStereoChange(true) },
                            label = { Text("Stereo") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Sample Rate", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.sampleRate == 16000,
                            onClick = { onSampleRateChange(16000) },
                            label = { Text("16 kHz") }
                        )
                        FilterChip(
                            selected = settings.sampleRate == 44100,
                            onClick = { onSampleRateChange(44100) },
                            label = { Text("44.1 kHz") }
                        )
                        FilterChip(
                            selected = settings.sampleRate == 48000,
                            onClick = { onSampleRateChange(48000) },
                            label = { Text("48 kHz") }
                        )
                    }
                }
            }

            // Audio Enhancements Section
            SettingsSection(title = "Audio Enhancements") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(String.format(Locale.US, "Mic Gain: %.1fx", settings.micGain), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.micGain,
                        onValueChange = onMicGainChange,
                        valueRange = 0.1f..5.0f,
                        steps = 49
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(content = content)
        }
    }
}
