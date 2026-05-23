package com.atomx.genericprinter

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager

class UsbPrinterConnection(
    private val usbManager: UsbManager,
    private val device: UsbDevice
) : PrinterConnection {

    private var connection: UsbDeviceConnection? = null
    private var intf: UsbInterface? = null
    private var endpointOut: UsbEndpoint? = null


    override fun connect(): Boolean {
        // 1. Permission check
        if (!usbManager.hasPermission(device)) {
            PrinterLogger.e("USB permission not granted for device: ${device.deviceName}")
            return false
        }

        // 2. Open device
        connection = usbManager.openDevice(device)
        if (connection == null) {
            PrinterLogger.e("Failed to open USB device")
            return false
        }

        PrinterLogger.d("USB device opened: ${device.deviceName}")

        // 3. Find BULK OUT endpoint
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)

            PrinterLogger.d("Checking interface $i with ${iface.endpointCount} endpoints")

            for (e in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(e)

                val isBulk = ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                val isOut = ep.direction == UsbConstants.USB_DIR_OUT

                PrinterLogger.d(
                    "Endpoint check -> iface=$i ep=$e type=${ep.type} dir=${ep.direction}"
                )

                if (isBulk && isOut) {
                    intf = iface
                    endpointOut = ep
                    PrinterLogger.d("BULK OUT endpoint found (iface=$i, ep=$e)")
                    break
                }
            }

            if (endpointOut != null) break
        }

        // 4. Validate endpoint and validate endpoint
        if (intf == null || endpointOut == null) {
            PrinterLogger.e("No BULK OUT endpoint found. This USB device is not an ESC/POS printer.")
            close()
            return false
        }

        // 5. Claim interface
        val claimed = connection!!.claimInterface(intf, true)
        if (!claimed) {
            PrinterLogger.e("Failed to claim USB interface")
            close()
            return false
        }

        PrinterLogger.d("USB printer connected successfully: ${device.deviceName}")
        return true
    }


    override fun isConnected(): Boolean = connection != null && endpointOut != null

    override fun write(bytes: ByteArray) {
        val conn = connection ?: throw IllegalStateException("USB printer not connected")
        val ep = endpointOut ?: throw IllegalStateException("USB endpoint not available")

        // bulkTransfer returns bytes written or negative on failure
        val res = conn.bulkTransfer(ep, bytes, bytes.size, 5000)
        if (res < 0) throw RuntimeException("USB bulkTransfer failed")
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

        connection = null
        intf = null
        endpointOut = null
    }
}
