package com.example.expenseapp.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isConnected = MutableStateFlow(checkInitialConnectivity())
    val isConnected = _isConnected.asStateFlow()

    companion object {
        private const val TAG = "NetworkMonitor"
    }

    fun startMonitoring(onConnectionRestored: suspend () -> Unit, scope: CoroutineScope) {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val wasOffline = !_isConnected.value
                _isConnected.value = true
                Log.d(TAG, "Network became available. Was offline previously: $wasOffline")
                if (wasOffline) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            onConnectionRestored()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error executing onConnectionRestored", e)
                        }
                    }
                }
            }

            override fun onLost(network: Network) {
                val currentStatus = checkInitialConnectivity()
                _isConnected.value = currentStatus
                Log.d(TAG, "Network lost. Active internet remaining: $currentStatus")
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, callback)
            Log.d(TAG, "Registered NetworkCallback successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register NetworkCallback", e)
        }
    }

    fun checkInitialConnectivity(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
