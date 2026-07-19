package com.kor.genericprinter

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager

class UsbPrinterConnection(
    private val usbManager: UsbManager,
    private val device: UsbDevice? = null,
    private val writeTimeoutMillis: Int = 5000,
    private val writeChunkSize: Int = 16 * 1024
) : PrinterConnection {

    private var selectedDevice: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var intf: UsbInterface? = null
    private var endpointOut: UsbEndpoint? = null

    override fun connect(): Boolean {
        selectedDevice = device ?: findFirstUsbPrinter(usbManager)

        val printerDevice = selectedDevice
        if (printerDevice == null) {
            PrinterLogger.e("No USB printer found")
            return false
        }

        if (!usbManager.hasPermission(printerDevice)) {
            PrinterLogger.e("USB permission not granted for device: ${printerDevice.deviceName}")
            return false
        }

        connection = usbManager.openDevice(printerDevice)
        if (connection == null) {
            PrinterLogger.e("Failed to open USB device")
            return false
        }

        for (i in 0 until printerDevice.interfaceCount) {
            val iface = printerDevice.getInterface(i)

            for (e in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(e)

                val isBulk = ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                val isOut = ep.direction == UsbConstants.USB_DIR_OUT

                if (isBulk && isOut) {
                    intf = iface
                    endpointOut = ep
                    break
                }
            }

            if (endpointOut != null) break
        }

        if (intf == null || endpointOut == null) {
            PrinterLogger.e("No BULK OUT endpoint found")
            close()
            return false
        }

        val claimed = connection!!.claimInterface(intf, true)
        if (!claimed) {
            PrinterLogger.e("Failed to claim USB interface")
            close()
            return false
        }

        PrinterLogger.d("USB printer connected: ${printerDevice.deviceName}")
        return true
    }

    fun getSelectedDevice(): UsbDevice? = selectedDevice

    override fun isConnected(): Boolean {
        return connection != null && endpointOut != null
    }

    override fun write(bytes: ByteArray) {
        if (bytes.isEmpty()) return

        val conn = connection ?: throw IllegalStateException("USB printer not connected")
        val ep = endpointOut ?: throw IllegalStateException("USB endpoint not available")

        var offset = 0
        while (offset < bytes.size) {
            val length = minOf(writeChunkSize, bytes.size - offset)
            val result = conn.bulkTransfer(
                ep,
                bytes,
                offset,
                length,
                writeTimeoutMillis
            )

            if (result <= 0) {
                throw RuntimeException("USB bulkTransfer failed after $offset bytes")
            }

            offset += result
        }
    }

    override fun close() {
        try {
            val conn = connection
            val iface = intf

            if (conn != null && iface != null) {
                conn.releaseInterface(iface)
            }
        } catch (_: Throwable) {}

        try { connection?.close() } catch (_: Throwable) {}

        selectedDevice = null
        connection = null
        intf = null
        endpointOut = null
    }

    companion object {
        fun findFirstUsbPrinter(usbManager: UsbManager): UsbDevice? {
            return usbManager.deviceList.values.firstOrNull(::isLikelyPrinter)
        }

        fun isLikelyPrinter(device: UsbDevice): Boolean {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)

                for (e in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(e)

                    val isBulk = ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                    val isOut = ep.direction == UsbConstants.USB_DIR_OUT

                    if (isBulk && isOut) return true
                }
            }

            return false
        }

        fun displayName(device: UsbDevice): String {
            val productName = device.productName
            val manufacturerName = device.manufacturerName
            return when {
                !productName.isNullOrBlank() && !manufacturerName.isNullOrBlank() ->
                    "$manufacturerName $productName"
                !productName.isNullOrBlank() -> productName
                !manufacturerName.isNullOrBlank() -> manufacturerName
                else -> "USB printer ${device.vendorId}:${device.productId}"
            }
        }
    }
}
