package com.example.mikucamera.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat

class LocationProvider(private val context: Context) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun request(onResult: (Location?) -> Unit) {
        if (!hasPermission()) {
            onResult(null)
            return
        }
        val provider = when {
            runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) -> LocationManager.GPS_PROVIDER
            runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            onResult(null)
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.getCurrentLocation(provider, null, context.mainExecutor) { onResult(it ?: lastKnown(provider)) }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        onResult(location)
                    }
                    override fun onProviderDisabled(provider: String) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                manager.requestLocationUpdates(provider, 1_000L, 1f, listener, Looper.getMainLooper())
                context.mainExecutor.execute { onResult(lastKnown(provider)) }
            }
        } catch (_: SecurityException) {
            onResult(null)
        }
    }

    private fun lastKnown(provider: String): Location? = try {
        manager.getLastKnownLocation(provider)
    } catch (_: SecurityException) {
        null
    }

    private fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}
