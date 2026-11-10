package com.mabani.mdm

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var tokenInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If this activity was launched by the system right after
        // zero-touch/QR provisioning finished, the system expects this
        // activity to report success and finish() immediately -- it does
        // NOT want permission dialogs, a layout, or any other UI shown at
        // this point. Returning straight away (before setContentView and
        // before touching any views) avoids the provisioning wizard
        // hanging/failing with "Something went wrong".
        if (intent?.action == "android.app.action.ADMIN_POLICY_COMPLIANCE") {
            HeartbeatService.start(this)
            setResult(RESULT_OK)
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        tokenInput = findViewById(R.id.tokenInput)
        val enrollButton = findViewById<Button>(R.id.enrollButton)
        val requestAdminButton = findViewById<Button>(R.id.requestAdminButton)

        Prefs.enrollToken(this)?.let { tokenInput.setText(it) }

        // Safety net: if the heartbeat service ever died (e.g. after an
        // app update, before the PACKAGE_REPLACED fix, or for any other
        // reason) and nothing restarted it, simply opening the app brings
        // it back without needing a full device reboot.
        if (Prefs.isEnrolled(this) || Prefs.enrollToken(this) != null) {
            HeartbeatService.start(this)
        }

        requestPermissionsIfNeeded()
        refreshStatus()

        enrollButton.setOnClickListener {
            val token = tokenInput.text.toString().trim()
            if (token.isEmpty()) {
                Toast.makeText(this, "اكتب كود التسجيل أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.saveEnrollToken(this, token)
            HeartbeatService.start(this)
            Toast.makeText(this, "تم حفظ الكود — جاري محاولة التسجيل بالخلفية...", Toast.LENGTH_LONG).show()
            statusText.text = "جاري محاولة التسجيل بالخادم..."
        }

        requestAdminButton.setOnClickListener {
            val admin = ComponentName(this, AdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(R.string.device_admin_description)
            )
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val isOwner = dpm.isDeviceOwnerApp(packageName)
        val isAdmin = dpm.isAdminActive(ComponentName(this, AdminReceiver::class.java))
        val enrolled = Prefs.isEnrolled(this)
        statusText.text = when {
            isOwner && enrolled -> "✅ الجهاز مسجّل بالكامل (Device Owner) ومتصل بالخادم"
            isOwner -> "✅ Device Owner مفعّل — بانتظار التسجيل بالخادم"
            isAdmin -> "⚠️ صلاحيات مسؤول أساسية فقط (مو Device Owner الكامل)"
            else -> "❌ ما في صلاحيات إدارية بعد"
        }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        }
    }
}
