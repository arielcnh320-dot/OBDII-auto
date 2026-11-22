package com.ariel.obdauto

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import java.util.UUID

class ObdReader(private val deviceName: String = "OBDII") {
    private var socket: BluetoothSocket? = null
    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    val isConnected get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    fun connect() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: error("Sin Bluetooth")
        val device = adapter.bondedDevices.firstOrNull { it.name == deviceName }
            ?: error("No encuentro $deviceName emparejado")
        adapter.cancelDiscovery()
        val s = device.createRfcommSocketToServiceRecord(sppUuid)
        s.connect()
        socket = s
        listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0").forEach { send(it) }
    }

    fun close() { try { socket?.close() } catch (_: Exception) {} ; socket = null }

    @Synchronized
    fun send(cmd: String): String {
        val s = socket ?: error("No conectado")
        s.outputStream.write((cmd + "\r").toByteArray())
        s.outputStream.flush()
        val sb = StringBuilder()
        val input = s.inputStream
        while (true) {
            val b = input.read()
            if (b < 0 || b.toChar() == '>') break
            sb.append(b.toChar())
        }
        return sb.toString().replace("\r", " ").replace("\n", " ").trim()
    }

    private fun pidByte(pid: String): Int? {
        val r = send("01$pid").replace(" ", "").uppercase()
        val i = r.indexOf("41$pid")
        if (i < 0 || r.length < i + 6) return null
        return r.substring(i + 4, i + 6).toIntOrNull(16)
    }

    fun coolantTemp(): Int? = pidByte("05")?.minus(40)
    fun oilTemp(): Int? = pidByte("5C")?.minus(40)
    fun voltage(): String? = send("ATRV").takeIf { it.contains("V") }

    fun troubleCodes(): List<String>? {
        val raw = send("03").uppercase()
        if (raw.contains("NO DATA")) return emptyList()
        val r = raw.replace(Regex("[0-9A-F]:"), "").replace(" ", "")
        val i = r.indexOf("43")
        if (i < 0) return null
        var data = r.substring(i + 2)
        val count = data.take(2).toIntOrNull(16)
        if (count != null && data.length >= 2 + count * 4) {
            data = data.substring(2, 2 + count * 4)
        }
        return data.chunked(4)
            .filter { it.length == 4 && it != "0000" }
            .mapNotNull { decode(it) }
            .distinct()
    }

    private fun decode(hex: String): String? {
        val a = hex.substring(0, 2).toIntOrNull(16) ?: return null
        val letter = "PCBU"[a shr 6]
        val d1 = (a shr 4) and 0x3
        val d2 = a and 0xF
        return "$letter$d1${d2.toString(16).uppercase()}${hex.substring(2)}"
    }
}
