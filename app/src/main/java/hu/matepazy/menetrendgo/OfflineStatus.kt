package hu.matepazy.menetrendgo

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Whether the app is in offline mode: Android reports no working internet, or MÁVPlusz stopped answering.
 * The provider flag clears on the next successful request, or as soon as the connection comes back. */
object OfflineStatus {
    var deviceOffline by mutableStateOf(false); private set
    var providerDown by mutableStateOf(false); private set
    val active get() = deviceOffline || providerDown
    @Volatile private var registered = false

    fun init(context: Context) {
        if (registered) return
        registered = true
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        deviceOffline = !online(manager.getNetworkCapabilities(manager.activeNetwork))
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = update(online(capabilities))
            override fun onLost(network: Network) = update(false)
        })
    }

    private fun online(capabilities: NetworkCapabilities?) = capabilities != null &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private fun update(online: Boolean) {
        // A returning connection gives the provider a fresh chance instead of keeping an old failure.
        if (online && deviceOffline) providerDown = false
        deviceOffline = !online
    }

    fun providerFailed() { if (!providerDown) providerDown = true }
    fun providerReachable() { if (providerDown) providerDown = false }
}
