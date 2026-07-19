package com.atomx.genericprinter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.OutputStream
import java.util.UUID

class BluetoothPrinterConnection(
    private val macAddress: String? = null,
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter(),
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"),
    private val writeChunkSize: Int = 2048
) : PrinterConnection {

    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null

    @SuppressLint("MissingPermission")
    override fun connect(): Boolean {
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null) {
            PrinterLogger.e("Bluetooth is not available on this device")
            return false
        }

        if (!bluetoothAdapter.isEnabled) {
            PrinterLogger.e("Bluetooth is disabled")
            return false
        }

        val device: BluetoothDevice? = if (!macAddress.isNullOrBlank()) {
            bluetoothAdapter.bondedDevices.firstOrNull { it.address == macAddress }
        } else {
            bluetoothAdapter.bondedDevices.firstOrNull()
        }

        if (device == null) {
            PrinterLogger.e("No paired Bluetooth printer found")
            return false
        }

        PrinterLogger.d("Selected Bluetooth printer: ${device.name} - ${device.address}")

        if (tryCreateAndConnect(device, insecure = true)) return true
        if (tryCreateAndConnect(device, insecure = false)) return true

        return tryReflectionConnect(device)
    }

    @SuppressLint("MissingPermission")
    private fun tryCreateAndConnect(device: BluetoothDevice, insecure: Boolean): Boolean {
        return try {
            close()

            socket = if (insecure) {
                device.createInsecureRfcommSocketToServiceRecord(uuid)
            } else {
                device.createRfcommSocketToServiceRecord(uuid)
            }

            socket?.connect()
            out = socket?.outputStream

            PrinterLogger.d("Bluetooth connected: ${device.address}")
            true
        } catch (t: Throwable) {
            PrinterLogger.e("Bluetooth connect failed: ${t.message}", t)
            false
        }
    }

    @SuppressLint("DiscouragedPrivateApi", "MissingPermission")
    private fun tryReflectionConnect(device: BluetoothDevice): Boolean {
        return try {
            close()

            val method = device.javaClass.getMethod(
                "createRfcommSocket",
                Int::class.javaPrimitiveType
            )

            socket = method.invoke(device, 1) as BluetoothSocket
            socket?.connect()
            out = socket?.outputStream

            PrinterLogger.d("Bluetooth connected using reflection: ${device.address}")
            true
        } catch (t: Throwable) {
            PrinterLogger.e("Bluetooth reflection connect failed: ${t.message}", t)
            close()
            false
        }
    }

    override fun isConnected(): Boolean {
        return socket?.isConnected == true && out != null
    }

    override fun write(bytes: ByteArray) {
        if (!isConnected()) {
            throw IllegalStateException("Bluetooth printer not connected")
        }

        val stream = out ?: throw IllegalStateException("Bluetooth output stream not available")
        var offset = 0
        while (offset < bytes.size) {
            val length = minOf(writeChunkSize, bytes.size - offset)
            stream.write(bytes, offset, length)
            offset += length
        }
        stream.flush()
    }

    override fun close() {
        try { out?.flush() } catch (_: Throwable) {}
        try { out?.close() } catch (_: Throwable) {}
        try { socket?.close() } catch (_: Throwable) {}

        out = null
        socket = null
    }
}
