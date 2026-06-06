package com.ariok12.virtualmic

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper

class NsdHelper(context: Context) {
    private val nsdManager: NsdManager? = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val handler = Handler(Looper.getMainLooper())
    
    interface NsdCallback {
        fun onServerFound(ip: String)
        fun onError(message: String)
    }

    fun scanForPc(timeoutMillis: Long = 5000L, callback: NsdCallback) {
        if (nsdManager == null) {
            callback.onError("NSD Service not available on this device")
            return
        }
        
        stopDiscovery() // Ensure any previous scan is stopped
        
        var isResolved = false

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceName.contains("VirtualMic")) {
                    try {
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                if (!isResolved) {
                                    isResolved = true
                                    handler.post { callback.onError("Failed to resolve PC: Error $errorCode") }
                                    stopDiscovery()
                                }
                            }

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                val ip = serviceInfo.host?.hostAddress
                                if (ip != null && !isResolved) {
                                    isResolved = true
                                    handler.post { callback.onServerFound(ip) }
                                    stopDiscovery()
                                }
                            }
                        })
                    } catch (e: Exception) {
                        // resolveService can throw if another resolve is already in progress
                    }
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {}

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (!isResolved) {
                    isResolved = true
                    handler.post { callback.onError("Failed to start discovery: Error $errorCode") }
                    stopDiscovery()
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsdManager.stopServiceDiscovery(this) } catch (e: Exception) {}
            }
        }

        try {
            nsdManager.discoverServices("_virtualmic._udp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            
            // Timeout mechanism
            handler.postDelayed({
                if (!isResolved) {
                    isResolved = true
                    stopDiscovery()
                    callback.onError("Scan timed out")
                }
            }, timeoutMillis)
            
        } catch (e: Exception) {
            callback.onError(e.message ?: "Unknown error starting scan")
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager?.stopServiceDiscovery(it)
            } catch (e: Exception) {
                // Ignore, as it might already be stopped or not fully registered
            }
        }
        discoveryListener = null
    }
}
