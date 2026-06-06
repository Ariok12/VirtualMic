package com.ariok12.virtualmic

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.ariok12.virtualmic.ui.components.ConnectionStatusIndicator
import com.ariok12.virtualmic.ui.components.SettingsScreen
import com.ariok12.virtualmic.ui.components.VuMeter
import com.ariok12.virtualmic.ui.theme.VirtualMicTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>
    private var currentIpForMedia: String = ""
    private var pendingMediaAction: String = ""
    
    private var isStreaming by mutableStateOf(false)
    private var isBatteryOptimized by mutableStateOf(false)
    
    private lateinit var nsdHelper: NsdHelper
    private var isScanningPc by mutableStateOf(false)
    private var isMuted by mutableStateOf(false)
    
    private var ipAddress by mutableStateOf("")

    private lateinit var ipHistoryManager: IpHistoryManager
    private lateinit var settingsManager: SettingsManager
    
    private var currentAppSettings: AppSettings = AppSettings()

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MicService.ACTION_STATE_CHANGED) {
                isStreaming = intent.getBooleanExtra(MicService.EXTRA_IS_STREAMING, false)
                isMuted = intent.getBooleanExtra(MicService.EXTRA_IS_MUTED, false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        nsdHelper = NsdHelper(this)
        ipHistoryManager = IpHistoryManager(this)
        settingsManager = SettingsManager(this)

        val sharedPref = getPreferences(MODE_PRIVATE)
        ipAddress = sharedPref.getString("pc_ip", "") ?: ""

        mediaProjectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if ((result.resultCode == RESULT_OK) && (result.data != null)) {
                startMediaService(currentIpForMedia, pendingMediaAction, result.resultCode, result.data!!)
            } else {
                Toast.makeText(this, "Media capture permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

        setContent {
            val systemDark = isSystemInDarkTheme()
            val coroutineScope = rememberCoroutineScope()
            val ipHistory by ipHistoryManager.ipHistory.collectAsState(initial = emptyList())
            val appSettings by settingsManager.appSettingsFlow.collectAsState(initial = AppSettings(isDarkMode = systemDark))
            
            LaunchedEffect(appSettings) {
                currentAppSettings = appSettings
            }

            var showSettings by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                checkBatteryOptimization()
            }

            VirtualMicTheme(darkTheme = appSettings.isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (showSettings) {
                        SettingsScreen(
                            settings = appSettings,
                            onNavigateBack = { showSettings = false },
                            onMicGainChange = { coroutineScope.launch { settingsManager.updateMicGain(it) } },
                            onStereoChange = { coroutineScope.launch { settingsManager.updateIsStereo(it) } },
                            onSampleRateChange = { coroutineScope.launch { settingsManager.updateSampleRate(it) } },
                            onThemeChange = { coroutineScope.launch { settingsManager.updateIsDarkMode(it) } }
                        )
                    } else {
                        MainScreen(
                            ipAddress = ipAddress,
                            onIpChange = { 
                                ipAddress = it
                                sharedPref.edit { putString("pc_ip", it) }
                            },
                            ipHistory = ipHistory,
                            isStreaming = isStreaming,
                            onOpenSettings = { showSettings = true },
                            onStartMic = { 
                                startMicStream(ipAddress) 
                                coroutineScope.launch { ipHistoryManager.addIpToHistory(ipAddress) }
                            },
                            onStartMedia = {
                                pendingMediaAction = MicService.ACTION_START_MEDIA
                                startMediaStreamPrompt(ipAddress)
                                coroutineScope.launch { ipHistoryManager.addIpToHistory(ipAddress) }
                            },
                            onStartBoth = {
                                pendingMediaAction = MicService.ACTION_START_BOTH
                                startMediaStreamPrompt(ipAddress)
                                coroutineScope.launch { ipHistoryManager.addIpToHistory(ipAddress) }
                            },
                            isMuted = isMuted,
                            onMuteToggle = { toggleMute() },
                            onStop = { stopStreaming() },
                            isBatteryOptimized = isBatteryOptimized,
                            onRequestBatteryExemption = { requestBatteryOptimizationExemption() },
                            isScanning = isScanningPc,
                            onScanPc = { scanForPc() }
                        )
                    }
                }
            }
        }
        isStreaming = MicService.isServiceRunning
        isMuted = MicService.isMuted
        val filter = IntentFilter(MicService.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(this, stateReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        requestPermissions()
    }
    
    private fun scanForPc() {
        if (isScanningPc) return
        isScanningPc = true
        
        nsdHelper.scanForPc(timeoutMillis = 5000L, callback = object : NsdHelper.NsdCallback {
            override fun onServerFound(ip: String) {
                isScanningPc = false
                ipAddress = ip
                getPreferences(MODE_PRIVATE).edit { putString("pc_ip", ip) }
                Toast.makeText(this@MainActivity, "Found PC: $ip", Toast.LENGTH_SHORT).show()
            }

            override fun onError(message: String) {
                isScanningPc = false
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(stateReceiver)
        nsdHelper.stopDiscovery()
    }
    
    private fun putExtras(intent: Intent, ip: String) {
        intent.putExtra("EXTRA_IP", ip)
        intent.putExtra("EXTRA_MIC_GAIN", currentAppSettings.micGain)
        intent.putExtra("EXTRA_STEREO", currentAppSettings.isStereo)
        intent.putExtra("EXTRA_SAMPLE_RATE", currentAppSettings.sampleRate)
    }

    private fun startMicStream(ip: String) {
        if (ip.isBlank()) return Toast.makeText(this, "Please enter PC IP address", Toast.LENGTH_SHORT).show()

        if (hasPermissions()) {
            val intent = Intent(this, MicService::class.java).apply {
                action = MicService.ACTION_START_MIC
                putExtras(this, ip)
            }
            startForegroundService(intent)
            Toast.makeText(this, "Mic Stream Started", Toast.LENGTH_SHORT).show()
        } else requestPermissions()
    }

    private fun startMediaStreamPrompt(ip: String) {
        if (ip.isBlank()) return Toast.makeText(this, "Please enter PC IP address", Toast.LENGTH_SHORT).show()
        currentIpForMedia = ip

        if (hasPermissions()) {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
        } else requestPermissions()
    }

    private fun startMediaService(ip: String, actionType: String, resultCode: Int, data: Intent) {
        val intent = Intent(this, MicService::class.java).apply {
            action = actionType
            putExtras(this, ip)
            putExtra("RESULT_CODE", resultCode)
            putExtra("RESULT_DATA", data)
        }
        startForegroundService(intent)
        Toast.makeText(this, "Stream Started", Toast.LENGTH_SHORT).show()
    }

    private fun stopStreaming() {
        startService(Intent(this, MicService::class.java).apply { action = MicService.ACTION_STOP })
        Toast.makeText(this, "Streaming Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun toggleMute() {
        startService(Intent(this, MicService::class.java).apply { action = MicService.ACTION_MUTE })
    }

    private fun hasPermissions(): Boolean {
        val audioPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notifPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        return audioPerm && notifPerm
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
    }

    private fun checkBatteryOptimization() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        isBatteryOptimized = !pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryOptimizationExemption() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:$packageName".toUri()
            }
            startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        checkBatteryOptimization()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    ipAddress: String,
    onIpChange: (String) -> Unit,
    ipHistory: List<String>,
    isStreaming: Boolean,
    onOpenSettings: () -> Unit,
    onStartMic: () -> Unit,
    onStartMedia: () -> Unit,
    onStartBoth: () -> Unit,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    onStop: () -> Unit,
    isBatteryOptimized: Boolean,
    onRequestBatteryExemption: () -> Unit,
    isScanning: Boolean,
    onScanPc: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Virtual Mic", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isBatteryOptimized && !isStreaming) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    onClick = onRequestBatteryExemption
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Background Usage Restricted",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Tap to allow unrestricted battery usage for stable streaming.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Connection Indicator
            ConnectionStatusIndicator(isStreaming = isStreaming, ipAddress = ipAddress)

            if (isStreaming) {
                VuMeter(amplitudeFlow = MicService.amplitudeFlow)
            }

            // IP Input Card with Dropdown
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Connection Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var expanded by remember { mutableStateOf(false) }
                        
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = ipAddress,
                                onValueChange = onIpChange,
                                label = { Text("PC IP Address") },
                                placeholder = { Text("e.g. 192.168.1.15") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier.menuAnchor()
                            )
                            
                            if (ipHistory.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    ipHistory.forEach { ip ->
                                        DropdownMenuItem(
                                            text = { Text(ip) },
                                            onClick = {
                                                onIpChange(ip)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onScanPc, modifier = Modifier.size(56.dp)) {
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = "Scan PC", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            Text(
                "Streaming Modes",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            // Segmented Chips for Modes
            var selectedMode by remember { mutableStateOf("Mic") }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedMode == "Mic",
                    onClick = { selectedMode = "Mic"; if (!isStreaming) onStartMic() },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) {
                    Text("🎤 Mic")
                }
                SegmentedButton(
                    selected = selectedMode == "Media",
                    onClick = { selectedMode = "Media"; if (!isStreaming) onStartMedia() },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) {
                    Text("🎵 Media")
                }
                SegmentedButton(
                    selected = selectedMode == "Both",
                    onClick = { selectedMode = "Both"; if (!isStreaming) onStartBoth() },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) {
                    Text("🔄 Both")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (isStreaming) {
                Row(modifier = Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = onMuteToggle,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMuted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (isMuted) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isMuted) "UNMUTE" else "MUTE", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onStop,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("STOP", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            Text(
                "Make sure your PC is running the receiver app.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}