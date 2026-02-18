package com.virtualcoverage.signalmap.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.virtualcoverage.signalmap.R
import com.virtualcoverage.signalmap.service.SignalCollectionService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var isServiceRunning = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startSignalCollection()
        } else {
            Toast.makeText(this, "Permissions required for signal collection", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStartCollection = findViewById<Button>(R.id.btn_start_collection)
        val btnStopCollection = findViewById<Button>(R.id.btn_stop_collection)

        btnStartCollection.setOnClickListener {
            if (checkPermissions()) {
                startSignalCollection()
            } else {
                requestPermissions()
            }
        }

        btnStopCollection.setOnClickListener {
            stopSignalCollection()
        }
    }

    private fun checkPermissions(): Boolean {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(requiredPermissions.toTypedArray())
    }

    private fun startSignalCollection() {
        val intent = Intent(this, SignalCollectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isServiceRunning = true
        Toast.makeText(this, "Signal collection started", Toast.LENGTH_SHORT).show()
    }

    private fun stopSignalCollection() {
        val intent = Intent(this, SignalCollectionService::class.java)
        stopService(intent)
        isServiceRunning = false
        Toast.makeText(this, "Signal collection stopped", Toast.LENGTH_SHORT).show()
    }
}
