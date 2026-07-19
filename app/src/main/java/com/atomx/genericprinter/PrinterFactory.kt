package com.atomx.genericprinter

import android.bluetooth.BluetoothAdapter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

object PrinterFactory {

    fun bluetooth(
        config: PrinterConfig = PrinterConfig(),
        adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    ): PrinterClient {
        val conn = BluetoothPrinterConnection(
            macAddress = config.bluetoothMacAddress,
            adapter = adapter,
            writeChunkSize = config.bluetoothWriteChunkSize
        )

        return PrinterClient(conn, config)
    }

    fun bluetooth(
        macAddress: String,
        config: PrinterConfig = PrinterConfig(),
        adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    ): PrinterClient {
        val conn = BluetoothPrinterConnection(
            macAddress = macAddress,
            adapter = adapter,
            writeChunkSize = config.bluetoothWriteChunkSize
        )

        return PrinterClient(conn, config)
    }

    fun usb(
        usbManager: UsbManager,
        config: PrinterConfig = PrinterConfig()
    ): PrinterClient {
        val conn = UsbPrinterConnection(
            usbManager = usbManager,
            device = null,
            writeTimeoutMillis = config.usbWriteTimeoutMillis,
            writeChunkSize = config.usbWriteChunkSize
        )

        return PrinterClient(conn, config)
    }

    fun usb(
        usbManager: UsbManager,
        device: UsbDevice,
        config: PrinterConfig = PrinterConfig()
    ): PrinterClient {
        val conn = UsbPrinterConnection(
            usbManager = usbManager,
            device = device,
            writeTimeoutMillis = config.usbWriteTimeoutMillis,
            writeChunkSize = config.usbWriteChunkSize
        )

        return PrinterClient(conn, config)
    }
}
