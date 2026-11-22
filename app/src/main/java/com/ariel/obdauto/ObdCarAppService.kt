package com.ariel.obdauto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.*
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*

class ObdCarAppService : CarAppService() {
    override fun createHostValidator() = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession() = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = ObdScreen(carContext)
    }
}

class ObdScreen(ctx: CarContext) : Screen(ctx) {
    private val reader = ObdReader("OBDII")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var coolant = "--"
    private var oil = "--"
    private var volt = "--"
    private var dtc = "Revisando..."
    private var status = "Conectando..."
    private var knownCodes = emptySet<String>()
    private var lastDtcCheck = 0L

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { startLoop() }
            override fun onDestroy(owner: LifecycleOwner) { scope.cancel(); reader.close() }
        })
    }

    private fun startLoop() = scope.launch {
        while (isActive) {
            try {
                if (!reader.isConnected) { status = "Conectando..."; refresh(); reader.connect(); lastDtcCheck = 0 }
                coolant = reader.coolantTemp()?.let { "$it °C" } ?: "sin dato"
                delay(400)
                oil = reader.oilTemp()?.let { "$it °C" } ?: "no disponible"
                delay(400)
                volt = reader.voltage() ?: "sin dato"
                if (System.currentTimeMillis() - lastDtcCheck > 60_000) {
                    lastDtcCheck = System.currentTimeMillis()
                    val codes = reader.troubleCodes()
                    dtc = when {
                        codes == null -> "sin respuesta"
                        codes.isEmpty() -> "Sin fallas"
                        else -> codes.joinToString(", ")
                    }
                    val nuevos = (codes ?: emptyList()).filter { it !in knownCodes }
                    if (nuevos.isNotEmpty()) alert("⚠ Código de falla: " + nuevos.joinToString(", "))
                    knownCodes = codes?.toSet() ?: knownCodes
                }
                status = "Conectado"
            } catch (e: Exception) {
                status = "Error: ${e.message}"
                reader.close()
                delay(3000)
            }
            refresh()
            delay(5000)
        }
    }

    private suspend fun alert(msg: String) = withContext(Dispatchers.Main) {
        CarToast.makeText(carContext, msg, CarToast.LENGTH_LONG).show()
    }

    private suspend fun refresh() = withContext(Dispatchers.Main) { invalidate() }

    private fun row(title: String, value: String) =
        Row.Builder().setTitle(title).addText(value).build()

    override fun onGetTemplate(): Template {
        val pane = Pane.Builder()
            .addRow(row("Temperatura motor", coolant))
            .addRow(row("Temperatura aceite", oil))
            .addRow(row("Voltaje batería", volt))
            .addRow(row("Códigos de falla", dtc))
            .addRow(row("Estado", status))
            .build()
        return PaneTemplate.Builder(pane)
            .setTitle("OBD Auto")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
