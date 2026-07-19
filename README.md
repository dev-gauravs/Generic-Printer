# GenericPrinter

Lightweight ESC/POS thermal printer SDK for Android. It supports Bluetooth SPP printers, USB ESC/POS printers, built-in connection dialogs, USB attach/detach handling, text, bitmap printing, batched receipts, line feeds, cutter commands, alignment commands, and raw ESC/POS bytes.

## Installation

Add JitPack to the consuming app:

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }
    }
}
```

Add the library dependency:

```gradle
dependencies {
    implementation "com.github.KundalikSuryawanshi:Generic-Printer:1.1.2"
}
```

## Permissions

The library manifest declares Bluetooth and USB host capabilities. Your app should also request Bluetooth permission on Android 12+ before Bluetooth printing, or forward the permission result to `PrinterUiController`.

```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
```

## Ready-To-Use UI Flow

Create one controller in your `Activity` and close it from `onDestroy`.

```kotlin
class MainActivity : AppCompatActivity(), PrinterUiCallback {

    private lateinit var printer: PrinterUiController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        printer = PrinterUiController(
            activity = this,
            config = PrinterConfig(
                paperWidthPx = 384,
                debug = true
            ),
            callback = this
        )

        printer.startUsbAttachDetachFlow()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (!printer.handleRequestPermissionsResult(requestCode, permissions, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onDestroy() {
        printer.close()
        super.onDestroy()
    }
}
```

### Bluetooth Bitmap Print

The controller displays paired Bluetooth devices. After the user selects one, the SDK connects and sends the bitmap.

```kotlin
val bitmap = PrinterImageLoader.fromDrawable(this, R.drawable.receipt_logo)
printer.printBluetoothBitmap(bitmap)
```

If you already know the printer MAC address:

```kotlin
printer.printBluetoothBitmap(
    bitmap = bitmap,
    macAddress = "00:11:22:33:44:55"
)
```

### USB Printer Flow

Start USB monitoring once:

```kotlin
printer.startUsbAttachDetachFlow()
```

When a USB printer is attached, the library shows a dialog:

```text
Do you want to connect printer_name?
```

After the user taps OK, Android grants USB permission if needed, the SDK opens the printer, and the next print can be sent directly:

```kotlin
printer.printUsbBitmap(bitmap)
```

You can also show the same dialog for the currently attached USB printer:

```kotlin
printer.connectUsbPrinter()
```

## Low-Level API

Use `PrinterClient` directly when you want to build your own UI.

```kotlin
val printer = PrinterFactory.bluetooth(
    config = PrinterConfig(
        bluetoothMacAddress = "00:11:22:33:44:55",
        paperWidthPx = 384,
        debug = true
    )
)

if (printer.connect()) {
    printer.printText("GenericPrinter SDK")
    printer.feed(2)
    printer.cut()
    printer.disconnect()
}
```

For USB:

```kotlin
val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
val printer = PrinterFactory.usb(usbManager)

if (printer.connect()) {
    printer.printText("USB receipt")
    printer.feed()
    printer.disconnect()
}
```

## Optimized Receipt Printing

`printReceipt` batches the receipt into one byte array before sending it to the printer. This is faster and more reliable than many small writes, especially over Bluetooth.

```kotlin
printer.connectedClient()?.printReceipt {
    reset()
    align(PrinterAlignment.CENTER)
    text("My Store")
    text("Tax Invoice")
    align(PrinterAlignment.LEFT)
    text("Item A       100.00")
    text("Item B        50.00")
    feed(2)
    cut()
}
```

## Images

```kotlin
import com.atomx.genericprinter.utils.PrinterImageLoader

val bitmap = PrinterImageLoader.fromDrawable(context, R.drawable.receipt_logo)

printer.connectedClient()?.printBitmap(bitmap, center = true)
```

Default paper width is `384px`, which is common for 58mm thermal printers. Use `PrinterConfig(paperWidthPx = 576)` for many 80mm printers.

## Local Development

Build and test the SDK:

```bash
./gradlew :genericprinter:assembleRelease :genericprinter:testReleaseUnitTest
```

Publish to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

## Releasing With JitPack

1. Update `VERSION_NAME` in `gradle.properties`.
2. Commit the change.
3. Create and push a Git tag that matches the version, for example `1.1.2`.
4. Open `https://jitpack.io/#KundalikSuryawanshi/Generic-Printer`.
5. Ask consumers to use the same version in Gradle.

## License

MIT. See [LICENSE](LICENSE).
