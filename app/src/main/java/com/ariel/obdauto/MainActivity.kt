package com.ariel.obdauto

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "OBD Auto: aceptá el permiso de Bluetooth y abrí la app desde Android Auto."
        tv.setPadding(40, 80, 40, 40)
        setContentView(tv)
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
        }
    }
}
