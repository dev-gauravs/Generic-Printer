package com.kor.genericprinter


import android.util.Log

internal object PrinterLogger {
    private const val TAG = "AtomXPrinter"

    var enabled: Boolean = false

    fun d(msg: String) {
        if (enabled) Log.d(TAG, msg)
    }

    fun e(msg: String, t: Throwable? = null) {
        if (enabled) Log.e(TAG, msg, t)
    }
}
