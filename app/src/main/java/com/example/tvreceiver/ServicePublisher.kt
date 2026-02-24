package com.example.tvreceiver

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

class ServicePublisher(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun register(port: Int) {
        if (registrationListener != null) return

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "TVCast-${System.currentTimeMillis() % 10000}"
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    fun unregister() {
        registrationListener?.let {
            runCatching { nsdManager.unregisterService(it) }
        }
        registrationListener = null
    }

    companion object {
        const val SERVICE_TYPE = "_screencast._tcp."
    }
}
