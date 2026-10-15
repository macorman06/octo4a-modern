package com.jesper3d.tablet

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat

class PrinterService : Service() {
    private val controller get() = (application as PrinterApplication).printer
    private var deviceName: String? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val wakeHandler = Handler(Looper.getMainLooper())
    private val renewWakeLock = object : Runnable {
        override fun run() {
            wakeLock?.acquire(20 * 60 * 1000L)
            wakeHandler.postDelayed(this, 10 * 60 * 1000L)
        }
    }
    private val detach = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            if (device?.deviceName == deviceName) {
                controller.notice("Cable USB desconectado. La impresora puede seguir caliente.")
                controller.disconnect(null)
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        controller.onClosed = { stopSelf() }
        ContextCompat.registerReceiver(this, detach, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED), ContextCompat.RECEIVER_EXPORTED)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("printer", "Conexión con la impresora", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "DISCONNECT") { controller.disconnect(); stopSelf(); return START_NOT_STICKY }
        deviceName = intent?.getStringExtra("device")
        if (deviceName == null) { stopSelf(); return START_NOT_STICKY }
        val usb = getSystemService(USB_SERVICE) as UsbManager
        val device = usb.deviceList.values.firstOrNull { it.deviceName == deviceName }
        if (device == null || !usb.hasPermission(device)) {
            controller.notice("Falta el permiso USB o el cable se ha desconectado.")
            stopSelf()
            return START_NOT_STICKY
        }
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "printer").setSmallIcon(R.drawable.ic_printer)
            .setContentTitle("Jesper 3D · USB activo").setContentText("Conexión directa con la impresora")
            .setContentIntent(pending).setOngoing(true).build()
        ServiceCompat.startForeground(this, 1, notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jesper3D:UsbSession")
                .apply { setReferenceCounted(false) }
            renewWakeLock.run()
        }
        controller.connect(deviceName!!, intent?.getIntExtra("baud", 115200) ?: 115200)
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        wakeHandler.removeCallbacks(renewWakeLock)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        unregisterReceiver(detach)
        controller.onClosed = null
        controller.disconnect(null)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}
