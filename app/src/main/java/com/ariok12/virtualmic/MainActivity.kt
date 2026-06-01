package com.ariok12.virtualmic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.core.net.toUri
import android.provider.Settings
import android.os.PowerManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.ariok12.virtualmic.ui.theme.VirtualMicTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>
    private var currentIpForMedia: String = ""
    private var pendingMediaAction: String = ""
    
    private var isStreaming by mutableStateOf(false)
    private var isBatteryOptimized by mutableStateOf(false)
    
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    
    // UI States that need persisting
    private var ipAddress by mutableStateOf("")
    private var isDarkMode by mutableStateOf(false)
    
    // removed audiofx state vars
    private var micGain by mutableStateOf(1f)
    private var isStereo by mutableStateOf(false)
    private var is48k by mutableStateOf(false)

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MicService.ACTION_STATE_CHANGED) {
                isStreaming = intent.getBooleanExtra(MicService.EXTRA_IS_STREAMING, false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager

        val sharedPref = getPreferences(MODE_PRIVATE)
        isDarkMode = sharedPref.getBoolean("is_dark_mode", false)
        ipAddress = sharedPref.getString("pc_ip", "") ?: ""
        // removed audiofx sharedpref read
        micGain = sharedPref.getFloat("mic_gain", 1f)
        isStereo = sharedPref.getBoolean("is_stereo", false)
        is48k = sharedPref.getBoolean("is_48k", false)

        mediaProjectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if ((result.resultCode == RESULT_OK) && (result.data != null)) {
                startMediaService(currentIpForMedia, pendingMediaAction, result.resultCode, result.data!!)
            } else {
                Toast.makeText(this, "Media capture permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

        setContent {
            val systemDark = isSystemInDarkTheme()
            LaunchedEffect(Unit) {
                checkBatteryOptimization()
                if (sharedPref.contains("is_dark_mode").not()) {
                    isDarkMode = systemDark
                }
            }

            VirtualMicTheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        ipAddress = ipAddress,
                        onIpChange = { 
                            ipAddress = it
                            sharedPref.edit { putString("pc_ip", it) }
                        },
                        isDarkMode = isDarkMode,
                        isStreaming = isStreaming,
                        onToggleTheme = {
                            isDarkMode = !isDarkMode
                            sharedPref.edit { putBoolean("is_dark_mode", isDarkMode) }
                        },
                        onStartMic = { startMicStream(ipAddress) },
                        onStartMedia = {
                            pendingMediaAction = MicService.ACTION_START_MEDIA
                            startMediaStreamPrompt(ipAddress)
                        },
                        onStartBoth = {
                            pendingMediaAction = MicService.ACTION_START_BOTH
                            startMediaStreamPrompt(ipAddress)
                        },
                        onStop = { stopStreaming() },
                        isBatteryOptimized = isBatteryOptimized,
                        onRequestBatteryExemption = { requestBatteryOptimizationExemption() },
                        onScanPc = { scanForPc() },
                        
                        // removed audiofx callbacks
                        micGain = micGain,
                        onMicGainChange = { micGain = it; sharedPref.edit { putFloat("mic_gain", it) } },
                        
                        isStereo = isStereo,
                        onIsStereoChange = { isStereo = it; sharedPref.edit { putBoolean("is_stereo", it) } },
                        
                        is48k = is48k,
                        onIs48kChange = { is48k = it; sharedPref.edit { putBoolean("is_48k", it) } }
                    )
                }
            }
        }
        isStreaming = MicService.isServiceRunning
        val filter = IntentFilter(MicService.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(this, stateReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        requestPermissions()
    }
    
    private fun scanForPc() {
        Toast.makeText(this, "Scanning for PC...", Toast.LENGTH_SHORT).show()
        discoveryListener?.let { 
            try { nsdManager?.stopServiceDiscovery(it) } catch (e: Exception) {}
        }
        
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceName.contains("VirtualMic")) {
                    nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val ip = serviceInfo.host.hostAddress
                            runOnUiThread {
                                if (ip != null) {
                                    ipAddress = ip
                                    getPreferences(MODE_PRIVATE).edit { putString("pc_ip", ip) }
                                    Toast.makeText(this@MainActivity, "Found PC: $ip", Toast.LENGTH_SHORT).show()
                                }
                            }
                            try { nsdManager?.stopServiceDiscovery(discoveryListener) } catch (e: Exception) {}
                        }
                    })
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsdManager?.stopServiceDiscovery(this) } catch (e: Exception) {}
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsdManager?.stopServiceDiscovery(this) } catch (e: Exception) {}
            }
        }
        try {
            nsdManager?.discoverServices("_virtualmic._udp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(stateReceiver)
        discoveryListener?.let { 
            try { nsdManager?.stopServiceDiscovery(it) } catch (e: Exception) {}
        }
    }
    
    private fun putExtras(intent: Intent, ip: String) {
        intent.putExtra("EXTRA_IP", ip)
        // removed audiofx intent extras
        intent.putExtra("EXTRA_MIC_GAIN", micGain)
        intent.putExtra("EXTRA_STEREO", isStereo)
        intent.putExtra("EXTRA_48K", is48k)
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
    isDarkMode: Boolean,
    isStreaming: Boolean,
    onToggleTheme: () -> Unit,
    onStartMic: () -> Unit,
    onStartMedia: () -> Unit,
    onStartBoth: () -> Unit,
    onStop: () -> Unit,
    isBatteryOptimized: Boolean,
    onRequestBatteryExemption: () -> Unit,
    onScanPc: () -> Unit,
    // removed audiofx state params
    micGain: Float,
    onMicGainChange: (Float) -> Unit,
    isStereo: Boolean,
    onIsStereoChange: (Boolean) -> Unit,
    is48k: Boolean,
    onIs48kChange: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Virtual Mic", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme"
                        )
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

            // Status Card
            val statusColor = if (isStreaming) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
            val statusIcon = if (isStreaming) Icons.Default.CloudUpload else Icons.Default.Wifi
            val statusText = if (isStreaming) "Streaming to $ipAddress" else "Ready to connect"
            val onStatusColor = if (isStreaming) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = statusColor),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        statusIcon,
                        contentDescription = null,
                        tint = onStatusColor,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Status",
                            style = MaterialTheme.typography.labelMedium,
                            color = onStatusColor.copy(alpha = 0.7f)
                        )
                        Text(
                            statusText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = onStatusColor
                        )
                    }
                }
            }

            // IP Input Card
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
                        OutlinedTextField(
                            value = ipAddress,
                            onValueChange = onIpChange,
                            label = { Text("PC IP Address") },
                            placeholder = { Text("e.g. 192.168.1.15") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onScanPc, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Default.Search, contentDescription = "Scan PC", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            
            // Advanced Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Audio Enhancements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // removed audiofx switches
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(String.format(Locale.US, "Mic Gain: %.1fx", micGain), style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = micGain,
                        onValueChange = onMicGainChange,
                        valueRange = 0.1f..5.0f,
                        steps = 49
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Format Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !isStereo,
                            onClick = { onIsStereoChange(false) },
                            label = { Text("Mono") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = isStereo,
                            onClick = { onIsStereoChange(true) },
                            label = { Text("Stereo") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !is48k,
                            onClick = { onIs48kChange(false) },
                            label = { Text("44.1 kHz") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = is48k,
                            onClick = { onIs48kChange(true) },
                            label = { Text("48 kHz") },
                            modifier = Modifier.weight(1f)
                        )
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

            // Grid-like buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StreamModeButton(
                    modifier = Modifier.weight(1f),
                    title = "Mic Only",
                    icon = Icons.Default.Mic,
                    color = Color(0xFF4CAF50),
                    enabled = !isStreaming,
                    onClick = onStartMic
                )
                StreamModeButton(
                    modifier = Modifier.weight(1f),
                    title = "Media Only",
                    icon = Icons.Default.Monitor,
                    color = Color(0xFF2196F3),
                    enabled = !isStreaming,
                    onClick = onStartMedia
                )
            }

            StreamModeButton(
                modifier = Modifier.fillMaxWidth(),
                title = "Both (Mic + Media)",
                icon = Icons.Default.GraphicEq,
                color = Color(0xFF9C27B0),
                enabled = !isStreaming,
                onClick = onStartBoth
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            if (isStreaming) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("STOP STREAMING", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
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

@Composable
fun StreamModeButton(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor = color
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}