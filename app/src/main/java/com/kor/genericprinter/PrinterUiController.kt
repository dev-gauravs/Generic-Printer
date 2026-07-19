package com.kor.genericprinter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable

class PrinterUiController(
    private val activity: Activity,
    private val config: PrinterConfig = PrinterConfig(),
    private val callback: PrinterUiCallback? = null,
    private val bluetoothRequestCode: Int = DEFAULT_BLUETOOTH_REQUEST_CODE
) : Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val usbManager: UsbManager by lazy {
        activity.getSystemService(Context.USB_SERVICE) as UsbManager
    }
    private val usbPermissionAction = "${activity.packageName}.genericprinter.USB_PERMISSION"

    private var activeClient: PrinterClient? = null
    private var activeTransport: PrinterTransport? = null
    private var activeName: String? = null
    private var connectedUsbDevice: UsbDevice? = null
    private var pendingBluetoothAction: (() -> Unit)? = null
    private var pendingUsbDevice: UsbDevice? = null
    private var pendingUsbAfterConnect: ((PrinterClient, String) -> Unit)? = null
    private var usbReceiverRegistered = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                usbPermissionAction -> {
                    val device = intent.usbDeviceExtra() ?: pendingUsbDevice
                    val granted = intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED,
                        false
                    )

                    if (device == null) {
                        reportError(PrinterTransport.USB, "USB permission result did not include a printer")
                        return
                    }

                    if (granted) {
                        openUsbDevice(device, pendingUsbAfterConnect)
                    } else {
                        reportError(
                            PrinterTransport.USB,
                            "USB permission denied for ${UsbPrinterConnection.displayName(device)}"
                        )
                    }

                    pendingUsbDevice = null
                    pendingUsbAfterConnect = null
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = intent.usbDeviceExtra()
                    if (device != null && UsbPrinterConnection.isLikelyPrinter(device)) {
                        connectUsbPrinter(device)
                    }
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.usbDeviceExtra()
                    if (device != null && isSameDevice(device, connectedUsbDevice)) {
                        val name = activeName ?: UsbPrinterConnection.displayName(device)
                        disconnect()
                        Toast.makeText(activity, "$name disconnected", Toast.LENGTH_SHORT).show()
                        callback?.onPrinterDisconnected(PrinterTransport.USB, name)
                    }
                }
            }
        }
    }

    fun startUsbAttachDetachFlow() {
        if (usbReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(usbPermissionAction)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            activity.registerReceiver(usbReceiver, filter)
        }

        usbReceiverRegistered = true
    }

    fun stopUsbAttachDetachFlow() {
        if (!usbReceiverRegistered) return
        try {
            activity.unregisterReceiver(usbReceiver)
        } catch (_: Throwable) {
        }
        usbReceiverRegistered = false
    }

    @SuppressLint("MissingPermission")
    fun connectBluetooth(macAddress: String? = config.bluetoothMacAddress) {
        connectBluetoothInternal(macAddress, afterConnect = null)
    }

    fun connectUsbPrinter(device: UsbDevice? = null) {
        connectUsbPrinterInternal(device, afterConnect = null)
    }

    fun printBluetoothBitmap(
        bitmap: Bitmap,
        macAddress: String? = config.bluetoothMacAddress,
        center: Boolean = true
    ) {
        connectBluetoothInternal(macAddress) { client, name ->
            printOnClient(client, PrinterTransport.BLUETOOTH, name) {
                printBitmap(bitmap, center)
            }
        }
    }

    fun printUsbBitmap(
        bitmap: Bitmap,
        device: UsbDevice? = connectedUsbDevice,
        center: Boolean = true
    ) {
        val client = activeClient
        if (client != null && activeTransport == PrinterTransport.USB && client.isConnected()) {
            printOnClient(client, PrinterTransport.USB, activeName ?: "USB printer") {
                printBitmap(bitmap, center)
            }
            return
        }

        connectUsbPrinterInternal(device) { connectedClient, name ->
            printOnClient(connectedClient, PrinterTransport.USB, name) {
                printBitmap(bitmap, center)
            }
        }
    }

    fun printBitmap(bitmap: Bitmap, center: Boolean = true) {
        val client = activeClient
        val transport = activeTransport
        val name = activeName

        if (client == null || transport == null || name == null || !client.isConnected()) {
            reportError(null, "Printer is not connected")
            return
        }

        printOnClient(client, transport, name) {
            printBitmap(bitmap, center)
        }
    }

    fun handleRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode != bluetoothRequestCode) return false

        val index = permissions.indexOf(Manifest.permission.BLUETOOTH_CONNECT)
        val granted = index >= 0 && grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
        val action = pendingBluetoothAction
        pendingBluetoothAction = null

        if (granted && action != null) {
            action.invoke()
        } else if (!granted) {
            reportError(PrinterTransport.BLUETOOTH, "Bluetooth permission denied")
        }

        return true
    }

    fun disconnect() {
        activeClient?.disconnect()
        activeClient = null
        activeTransport = null
        activeName = null
        connectedUsbDevice = null
    }

    fun connectedClient(): PrinterClient? = activeClient

    override fun close() {
        stopUsbAttachDetachFlow()
        disconnect()
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun connectBluetoothInternal(
        macAddress: String?,
        afterConnect: ((PrinterClient, String) -> Unit)?
    ) {
        if (!hasBluetoothConnectPermission()) {
            pendingBluetoothAction = {
                connectBluetoothInternal(macAddress, afterConnect)
            }
            activity.requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                bluetoothRequestCode
            )
            callback?.onBluetoothPermissionRequested(bluetoothRequestCode)
            return
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            reportError(PrinterTransport.BLUETOOTH, "Bluetooth is not available on this device")
            return
        }

        if (!adapter.isEnabled) {
            showBluetoothDisabledDialog()
            return
        }

        val devices = adapter.bondedDevices
            .orEmpty()
            .sortedWith(compareBy<BluetoothDevice> { it.name ?: "" }.thenBy { it.address })

        if (!macAddress.isNullOrBlank()) {
            val device = devices.firstOrNull { it.address == macAddress }
            if (device == null) {
                reportError(PrinterTransport.BLUETOOTH, "Paired Bluetooth printer not found")
                return
            }
            openBluetoothDevice(device, afterConnect)
            return
        }

        if (devices.isEmpty()) {
            reportError(PrinterTransport.BLUETOOTH, "No paired Bluetooth printer found")
            return
        }

        val labels = devices.map(::bluetoothDisplayName).toTypedArray()
        showDialog(
            AlertDialog.Builder(activity)
                .setTitle("Select Bluetooth printer")
                .setItems(labels) { _, which ->
                    openBluetoothDevice(devices[which], afterConnect)
                }
                .setNegativeButton(android.R.string.cancel, null)
        )
    }

    @SuppressLint("MissingPermission")
    private fun openBluetoothDevice(
        device: BluetoothDevice,
        afterConnect: ((PrinterClient, String) -> Unit)?
    ) {
        val name = bluetoothDisplayName(device)
        val client = PrinterFactory.bluetooth(
            macAddress = device.address,
            config = config.copy(bluetoothMacAddress = device.address)
        )

        scope.launch {
            val connected = withContext(Dispatchers.IO) { client.connect() }
            if (connected) {
                replaceActiveClient(client, PrinterTransport.BLUETOOTH, name, usbDevice = null)
                Toast.makeText(activity, "$name connected", Toast.LENGTH_SHORT).show()
                callback?.onPrinterConnected(client, PrinterTransport.BLUETOOTH, name)
                afterConnect?.invoke(client, name)
            } else {
                client.close()
                reportError(PrinterTransport.BLUETOOTH, "Could not connect $name")
            }
        }
    }

    private fun connectUsbPrinterInternal(
        device: UsbDevice?,
        afterConnect: ((PrinterClient, String) -> Unit)?
    ) {
        startUsbAttachDetachFlow()

        val printerDevice = device ?: UsbPrinterConnection.findFirstUsbPrinter(usbManager)
        if (printerDevice == null) {
            reportError(PrinterTransport.USB, "No USB printer found")
            return
        }

        val name = UsbPrinterConnection.displayName(printerDevice)
        showDialog(
            AlertDialog.Builder(activity)
                .setTitle("Connect USB printer?")
                .setMessage("Do you want to connect $name?")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    if (usbManager.hasPermission(printerDevice)) {
                        openUsbDevice(printerDevice, afterConnect)
                    } else {
                        requestUsbPermission(printerDevice, afterConnect)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
        )
    }

    private fun requestUsbPermission(
        device: UsbDevice,
        afterConnect: ((PrinterClient, String) -> Unit)?
    ) {
        pendingUsbDevice = device
        pendingUsbAfterConnect = afterConnect

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }

        val intent = Intent(usbPermissionAction).setPackage(activity.packageName)
        val pendingIntent = PendingIntent.getBroadcast(activity, 0, intent, flags)
        usbManager.requestPermission(device, pendingIntent)
    }

    private fun openUsbDevice(
        device: UsbDevice,
        afterConnect: ((PrinterClient, String) -> Unit)?
    ) {
        val name = UsbPrinterConnection.displayName(device)
        val client = PrinterFactory.usb(usbManager, device, config)

        scope.launch {
            val connected = withContext(Dispatchers.IO) { client.connect() }
            if (connected) {
                replaceActiveClient(client, PrinterTransport.USB, name, usbDevice = device)
                Toast.makeText(activity, "$name connected", Toast.LENGTH_SHORT).show()
                callback?.onPrinterConnected(client, PrinterTransport.USB, name)
                afterConnect?.invoke(client, name)
            } else {
                client.close()
                reportError(PrinterTransport.USB, "Could not connect $name")
            }
        }
    }

    private fun printOnClient(
        client: PrinterClient,
        transport: PrinterTransport,
        name: String,
        printBlock: PrinterClient.() -> Unit
    ) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    client.printBlock()
                }
                callback?.onPrintComplete(transport, name)
            } catch (t: Throwable) {
                reportError(transport, "Could not print on $name", t)
            }
        }
    }

    private fun replaceActiveClient(
        client: PrinterClient,
        transport: PrinterTransport,
        name: String,
        usbDevice: UsbDevice?
    ) {
        if (activeClient !== client) {
            activeClient?.disconnect()
        }
        activeClient = client
        activeTransport = transport
        activeName = name
        connectedUsbDevice = usbDevice
    }

    private fun reportError(
        transport: PrinterTransport?,
        message: String,
        throwable: Throwable? = null
    ) {
        PrinterLogger.e(message, throwable)
        callback?.onPrinterError(transport, message, throwable)
        showDialog(
            AlertDialog.Builder(activity)
                .setTitle("Printer")
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
        )
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun showBluetoothDisabledDialog() {
        showDialog(
            AlertDialog.Builder(activity)
                .setTitle("Bluetooth is off")
                .setMessage("Turn on Bluetooth to connect a printer.")
                .setPositiveButton("Settings") { _, _ ->
                    activity.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
                .setNegativeButton(android.R.string.cancel, null)
        )
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothDisplayName(device: BluetoothDevice): String {
        val name = device.name
        return if (name.isNullOrBlank()) device.address else "$name (${device.address})"
    }

    private fun showDialog(builder: AlertDialog.Builder) {
        if (activity.isFinishing) return
        activity.runOnUiThread {
            if (!activity.isFinishing) {
                builder.show()
            }
        }
    }

    private fun isSameDevice(first: UsbDevice?, second: UsbDevice?): Boolean {
        return first != null && second != null && first.deviceName == second.deviceName
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDeviceExtra(): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }

    companion object {
        const val DEFAULT_BLUETOOTH_REQUEST_CODE = 4207
    }
}
