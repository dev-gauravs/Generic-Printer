package com.atomx.genericprinter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.OutputStream
import java.util.UUID

class BluetoothPrinterConnection(
    private val macAddress: String,
    private val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter(),
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SPP
) : PrinterConnection {

    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null

    @SuppressLint("MissingPermission")
    override fun connect(): Boolean {
        if (adapter.isEnabled.not()) {
            PrinterLogger.e("Bluetooth is disabled")
            return false
        }

        val device: BluetoothDevice? = adapter.bondedDevices.firstOrNull { it.address == macAddress }
        if (device == null) {
            PrinterLogger.e("Bluetooth device not bonded: $macAddress")
            return false
        }

        // Try insecure -> secure -> reflection fallback and test
        val s1 = tryCreateAndConnect(device, insecure = true)
        if (s1) return true

        val s2 = tryCreateAndConnect(device, insecure = false)
        if (s2) return true

        val s3 = tryReflectionConnect(device)
        return s3
    }

    @SuppressLint("MissingPermission")
    private fun tryCreateAndConnect(device: BluetoothDevice, insecure: Boolean): Boolean {
        return try {
            close()
            socket = if (insecure) {
                PrinterLogger.d("BT connect: createInsecureRfcommSocketToServiceRecord")
                device.createInsecureRfcommSocketToServiceRecord(uuid)
            } else {
                PrinterLogger.d("BT connect: createRfcommSocketToServiceRecord")
                device.createRfcommSocketToServiceRecord(uuid)
            }
            socket?.connect()
            out = socket?.outputStream
            PrinterLogger.d("BT connected ($macAddress) insecure=$insecure")
            true
        } catch (t: Throwable) {
            PrinterLogger.e("BT connect failed insecure=$insecure : ${t.message}", t)
            false
        }
    }

    @SuppressLint("DiscouragedPrivateApi", "MissingPermission")
    private fun tryReflectionConnect(device: BluetoothDevice): Boolean {
        return try {
            close()
            PrinterLogger.d("BT connect: reflection createRfcommSocket(1)")
            val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            socket = m.invoke(device, 1) as BluetoothSocket
            socket?.connect()
            out = socket?.outputStream
            PrinterLogger.d("BT connected via reflection ($macAddress)")
            true
        } catch (t: Throwable) {
            PrinterLogger.e("BT reflection connect failed: ${t.message}", t)
            close()
            false
        }
    }

    override fun isConnected(): Boolean = socket?.isConnected == true && out != null

    override fun write(bytes: ByteArray) {
        if (!isConnected()) throw IllegalStateException("Bluetooth printer not connected")
        out?.write(bytes)
        out?.flush()
    }

    override fun close() {
        try { out?.flush() } catch (_: Throwable) {}
        try { out?.close() } catch (_: Throwable) {}
        try { socket?.close() } catch (_: Throwable) {}
        out = null
        socket = null
    }
}
