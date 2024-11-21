package com.zAllaham.publisher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ZAllaham.publisher.R
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private var client: Mqtt5BlockingClient? = null
    private lateinit var locationManager: LocationManager

    private var lastLocation: Location? = null
    private var lastUpdateTime: Long = 0

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {

            val speed = calculateSpeed(location)
            sendLocationToBroker(location.latitude, location.longitude, speed)
            lastLocation = location
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        client = Mqtt5Client.builder()
            .identifier(UUID.randomUUID().toString())
            .serverHost("broker-816034409.sundaebytestt.com")
            .serverPort(1883)
            .build()
            .toBlocking()

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        checkLocationPermissions()
    }

    private fun getStudentId(): String {
        val studentIdInput = findViewById<EditText>(R.id.student_ID)
        return studentIdInput.text.toString().trim()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Permission for location access is required", Toast.LENGTH_SHORT).show()
            return
        }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            5000,
            2f,
            locationListener
        )
        Toast.makeText(this, "Receiving location updates", Toast.LENGTH_SHORT).show()
    }

    private fun stopLocationUpdates() {
        locationManager.removeUpdates(locationListener)
        Toast.makeText(this, "Location updates have stopped", Toast.LENGTH_SHORT).show()
    }

    private fun sendLocationToBroker(latitude: Double, longitude: Double, speed: Float) {
        val studentId = getStudentId()
        val message = "StudentID: $studentId, Latitude: $latitude, Longitude: $longitude, Speed: $speed"

        try {
            client?.publishWith()?.topic("assignment/location")?.payload(message.toByteArray())?.send()
            Log.e("MQTT", "Location and speed sent: $message")
            Toast.makeText(this, "Data sent to broker", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send data to broker", Toast.LENGTH_SHORT).show()
        }
    }

    fun startPublishing(view: android.view.View) {
        try {
            client?.connect()
            Toast.makeText(this, "Connected to the broker", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to connect to the broker", Toast.LENGTH_SHORT).show()
        }
        startLocationUpdates()
    }

    fun stopPublishing(view: android.view.View) {
        stopLocationUpdates()
        try {
            client?.disconnect()
            Toast.makeText(this, "Disconnected from the broker", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to disconnect from the broker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1
            )
        }
    }

    private fun calculateSpeed(location: Location): Float {
        if (lastLocation == null) {
            return 0f
        }

        val distance = location.distanceTo(lastLocation!!)
        val timeElapsed = (location.time - lastLocation!!.time) / 1000.0

        return if (timeElapsed > 0) {
            distance.toFloat() / timeElapsed.toFloat() * 3.6f
        } else {
            0f
        }
    }
}
