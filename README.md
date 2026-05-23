# Generic Printer Library

Lightweight Android ESC/POS printer library supporting:
- Bluetooth printers
- USB printers
- Text printing
- Image printing
- Receipt printing

---

# Installation

## Step 1: Add JitPack

Add in `settings.gradle`

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

---

## Step 2: Add Dependency

```gradle
implementation 'com.github.dev-gauravs:Generic-Printer:1.1.1'
```

---

# Bluetooth Permissions

Add in `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.BLUETOOTH"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"/>
```

---

# Quick Example

```kotlin
val config = PrinterConfig(
    printerType = PrinterConfig.PrinterType.BLUETOOTH,
    bluetoothMacAddress = "00:11:22:33:44:55"
)

val printerClient = PrinterClient(config)

lifecycleScope.launch {

    try {

        printerClient.connect()

        printerClient.printText(
            "Hello Printer\n",
            bold = true,
            center = true
        )

        printerClient.printText("Print Success\n")

        printerClient.cutPaper()

        printerClient.disconnect()

    } catch (e: Exception) {
        e.printStackTrace()
    }
}
```

---

# Features

- ESC/POS Support
- Bluetooth Printing
- USB Printing
- Bitmap Printing
- Receipt Printing
- Kotlin Coroutines Support
- Lightweight Library

---

# Version

Current Version:

```gradle
1.1.1
```

---

# Author

Gaurav Suryawanshi
