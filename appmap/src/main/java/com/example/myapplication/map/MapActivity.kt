package com.example.myapplication.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.database.Cursor
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.example.core.db.MeasurementRepository
import com.example.core.db.MeasurementEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log

class MapActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val locationState = mutableStateOf<Location?>(null)
    private val locationStatusState = mutableStateOf("Location: idle")

    private val keepLastMeasurements = 3000
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var measurementRepository: MeasurementRepository

    private val measurementsState = mutableStateOf<List<MeasurementEntity>>(emptyList())

    private val providerDiagState = mutableStateOf("Provider: idle")

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            fetchLocationOnce()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        measurementRepository = MeasurementRepository.getInstance(this)

        Configuration.getInstance().userAgentValue = packageName
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            MaterialTheme {
                MapScreen(
                    location = locationState.value,
                    locationStatus = locationStatusState.value,
                    measurements = measurementsState.value,
                    onRefresh = { loadLatest() },
                    providerDiag = providerDiagState.value
                )
            }
        }

        ensureLocationPermissionThenFetch()
        loadLatest()

        // Auto-refresh: recharge périodiquement pour afficher les nouvelles mesures
        appScope.launch {
            while (true) {
                delay(5_000)
                loadLatestViaProvider()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appScope.cancel()
    }

    private fun loadLatest() {
        appScope.launch {
            loadLatestViaProvider()
        }
    }

    private suspend fun loadLatestViaProvider() {
        try {
            val uri = MEASUREMENTS_LATEST_URI.buildUpon()
                .appendQueryParameter("limit", keepLastMeasurements.toString())
                .build()

            val cursor = contentResolver.query(uri, null, null, null, null)
            val list = cursor?.use { c -> cursorToMeasurements(c) } ?: emptyList()

            val msg = "Provider: OK (rows=${list.size}) @${System.currentTimeMillis()}"
            Log.d("MapActivity", msg)

            runOnUiThread {
                providerDiagState.value = msg
                measurementsState.value = list
            }
        } catch (t: Throwable) {
            val msg = "Provider: ERROR ${t::class.java.simpleName}: ${t.message}"
            Log.e("MapActivity", msg, t)
            runOnUiThread {
                providerDiagState.value = msg
            }
        }
    }

    private fun cursorToMeasurements(c: Cursor): List<MeasurementEntity> {
        val out = ArrayList<MeasurementEntity>(c.count.coerceAtLeast(0))

        val idxId = c.getColumnIndex("id")
        val idxTs = c.getColumnIndex("timestampMs")
        val idxLat = c.getColumnIndex("latitude")
        val idxLon = c.getColumnIndex("longitude")
        val idxT = c.getColumnIndex("temperatureC")
        val idxH = c.getColumnIndex("humidityPct")
        val idxRaw = c.getColumnIndex("raw")

        while (c.moveToNext()) {
            val id = if (idxId >= 0) c.getLong(idxId) else 0L
            val ts = if (idxTs >= 0) c.getLong(idxTs) else 0L
            val lat = if (idxLat >= 0) c.getDouble(idxLat) else 0.0
            val lon = if (idxLon >= 0) c.getDouble(idxLon) else 0.0
            val t = if (idxT >= 0 && !c.isNull(idxT)) c.getDouble(idxT) else null
            val h = if (idxH >= 0 && !c.isNull(idxH)) c.getDouble(idxH) else null
            val raw = if (idxRaw >= 0) c.getString(idxRaw) ?: "" else ""

            out.add(
                MeasurementEntity(
                    id = id,
                    timestampMs = ts,
                    latitude = lat,
                    longitude = lon,
                    temperatureC = t,
                    humidityPct = h,
                    raw = raw
                )
            )
        }
        return out
    }

    override fun onResume() {
        super.onResume()
        fetchLocationOnce()
        loadLatest() // important: recharger les mesures quand on revient sur l'app carte
    }

    private fun ensureLocationPermissionThenFetch() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            fetchLocationOnce()
        } else {
            locationStatusState.value = "Location: requesting permission…"
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= 28) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationOnce() {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            locationStatusState.value = "Location: permission missing"
            return
        }

        if (!isLocationEnabled()) {
            locationStatusState.value = "Location: désactivée dans les réglages"
            locationState.value = null
            return
        }

        val priority = if (fineGranted) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        locationStatusState.value = "Location: fetching…"

        val cts = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(priority, cts.token)
            .addOnSuccessListener { loc: Location? ->
                locationState.value = loc
                locationStatusState.value = if (loc != null) "Location: OK" else "Location: null"
            }
            .addOnFailureListener { e: Exception ->
                locationStatusState.value = "Location error: ${e.message}"
            }
    }

    // (supprimé) addPointAtCurrentLocation(): on n'ajoute plus de points manuellement ici,
    // la carte affiche les mesures réellement enregistrées dans Room par l'app capteur.

    companion object {
        // Même authority que dans :app (MeasurementsProvider.AUTHORITY)
        private val MEASUREMENTS_LATEST_URI: Uri =
            Uri.parse("content://com.example.myapplication.measurements/latest")
    }
}

@Composable
private fun MapScreen(
    location: Location?,
    locationStatus: String,
    measurements: List<MeasurementEntity>,
    onRefresh: () -> Unit,
    providerDiag: String
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(scroll)
    ) {
        Text(locationStatus)
        Text("Lat: ${location?.latitude ?: "--"}")
        Text("Lon: ${location?.longitude ?: "--"}")
        Spacer(modifier = Modifier.height(8.dp))

        Text(providerDiag)
        Spacer(modifier = Modifier.height(8.dp))

        Text("Mesures: ${measurements.size} (max 3000)")
        val last = measurements.firstOrNull()
        if (last != null) {
            Text("Dernière: ${Date(last.timestampMs)}")
        }
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = onRefresh) { Text("Rafraîchir") }

        Spacer(modifier = Modifier.height(16.dp))

        OsmMap(
            modifier = Modifier
                .fillMaxWidth()
                .size(420.dp),
            currentLocation = location,
            measurements = measurements
        )
    }
}

@Composable
private fun OsmMap(
    modifier: Modifier,
    currentLocation: Location?,
    measurements: List<MeasurementEntity>
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = {
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(15.0)
            }
        },
        update = { mapView ->
            if (currentLocation != null) {
                mapView.controller.setCenter(GeoPoint(currentLocation.latitude, currentLocation.longitude))
            }

            mapView.overlays.clear()

            val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            for (m in measurements) {
                val marker = Marker(mapView).apply {
                    position = GeoPoint(m.latitude, m.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                    val t = m.temperatureC?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
                    val h = m.humidityPct?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
                    val ts = df.format(Date(m.timestampMs))

                    title = "T=$t°C  H=$h%"
                    subDescription = "$ts\n${m.raw}"
                }
                mapView.overlays.add(marker)
            }

            mapView.invalidate()
        }
    )
}
