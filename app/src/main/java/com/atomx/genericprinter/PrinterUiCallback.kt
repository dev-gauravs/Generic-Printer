package com.atomx.genericprinter

interface PrinterUiCallback {
    fun onPrinterConnected(
        client: PrinterClient,
        transport: PrinterTransport,
        name: String
    ) {}

    fun onPrinterDisconnected(
        transport: PrinterTransport,
        name: String
    ) {}

    fun onPrinterError(
        transport: PrinterTransport?,
        message: String,
        throwable: Throwable?
    ) {}

    fun onPrintComplete(
        transport: PrinterTransport,
        name: String
    ) {}

    fun onBluetoothPermissionRequested(requestCode: Int) {}
}
