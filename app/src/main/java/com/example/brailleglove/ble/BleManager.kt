package com.example.brailleglove.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.example.brailleglove.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.ArrayDeque
import java.util.UUID

/**
 * Manages the full BLE lifecycle:
 *   scan -> connect -> discover services -> send vibration -> receive taps
 *
 * Caller responsibilities:
 *  - Hold BLUETOOTH_SCAN, BLUETOOTH_CONNECT, ACCESS_FINE_LOCATION permissions before calling startScan().
 *  - Call disconnect() + destroy() from the owner's onDestroy().
 *
 * NOTE ON THE GATT UUIDs IN Constants.kt: they're placeholders
 * ("12345678-...-123456789abc" etc). There's no ESP32 firmware in this
 * codebase yet, so these are provisional — see /docs/BLE_GATT_CONTRACT.md
 * for the full spec the firmware needs to implement to match this client.
 */
class BleManager(private val context: Context) {

    // ── Connection state ──────────────────────────────────────────────────────

    enum class ConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // ── Battery: low/OK flag, not a percentage (null = unknown / not yet reported) ──

    private val _isBatteryLow = MutableStateFlow<Boolean?>(null)
    val isBatteryLow: StateFlow<Boolean?> = _isBatteryLow

    // ── Tap callback (called on main thread) ──────────────────────────────────

    /**
     * Set this in your ViewModel/Fragment.
     * Invoked whenever the glove sends a tap event.
     * @param BooleanArray 6-element array: true = dot pressed, indices match glove layout [0..5].
     */
    var onTapReceived: ((BooleanArray) -> Unit)? = null

    // ── Internal BLE objects ──────────────────────────────────────────────────

    private val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var gatt: BluetoothGatt? = null
    private var vibrationChar: BluetoothGattCharacteristic? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // True only while a reconnect attempt is in flight, so onConnectionStateChange
    // knows a subsequent CONNECTED means "reconnect succeeded" not "fresh connect".
    private var userInitiatedDisconnect = false
    private var reconnectAttempts = 0

    // ── GATT operation queue ──────────────────────────────────────────────────
    // Android's BluetoothGatt only supports ONE outstanding operation
    // (write/read/descriptor-write) at a time — firing a second one before the
    // first's callback (onCharacteristicWrite/onDescriptorWrite) returns is a
    // well-known source of silently-dropped operations on real hardware,
    // especially when subscribing to multiple characteristics back-to-back
    // right after service discovery. Every GATT wire operation goes through
    // this queue instead of being called directly.
    private val operationQueue = ArrayDeque<() -> Unit>()
    private var operationInFlight = false

    private fun enqueueGattOperation(op: () -> Unit) {
        synchronized(operationQueue) {
            operationQueue.addLast(op)
            if (!operationInFlight) {
                operationInFlight = true
                operationQueue.pollFirst()?.invoke()
            }
        }
    }

    private fun onGattOperationComplete() {
        synchronized(operationQueue) {
            val next = operationQueue.pollFirst()
            if (next != null) {
                next.invoke()
            } else {
                operationInFlight = false
            }
        }
    }

    private companion object {
        const val TAG = "BleManager"
        const val SCAN_TIMEOUT_MS = Constants.BLE_SCAN_TIMEOUT_MS
    }

    // ── Scan ──────────────────────────────────────────────────────────────────

    fun startScan() {
        if (_connectionState.value != ConnectionState.DISCONNECTED) return
        if (bluetoothAdapter?.isEnabled != true) {
            Log.w(TAG, "Bluetooth is off")
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(Constants.BLE_SERVICE_UUID)))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            _connectionState.value = ConnectionState.SCANNING
            scanner.startScan(listOf(filter), settings, scanCallback)
        } catch (e: SecurityException) {
            // Permission missing or revoked mid-session — fail safe instead of crashing.
            Log.e(TAG, "Missing BLE permission for startScan()", e)
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }

        // Auto-stop scan after timeout to save battery
        mainHandler.postDelayed({
            if (_connectionState.value == ConnectionState.SCANNING) {
                stopScan()
                Log.d(TAG, "Scan timed out — no glove found")
            }
        }, SCAN_TIMEOUT_MS)
    }

    fun stopScan() {
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLE permission for stopScan()", e)
        }
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d(TAG, "Found glove: ${result.device.address}")
            stopScan()
            connect(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with code $errorCode")
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    // ── Connect / Disconnect ──────────────────────────────────────────────────

    private fun connect(device: BluetoothDevice) {
        userInitiatedDisconnect = false
        reconnectAttempts = 0
        _connectionState.value = ConnectionState.CONNECTING
        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLE permission for connectGatt()", e)
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    /** Explicit user-requested disconnect — no auto-reconnect will follow this. */
    fun disconnect() {
        userInitiatedDisconnect = true
        // Cancel any reconnect already scheduled from a prior unexpected drop —
        // otherwise it fires later, calls .connect() on the gatt object we're
        // about to close() below, and can silently re-establish a connection
        // the user just explicitly asked to end.
        mainHandler.removeCallbacksAndMessages(null)
        clearOperationQueue()
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLE permission for disconnect()", e)
        }
        gatt = null
        vibrationChar = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun clearOperationQueue() {
        synchronized(operationQueue) {
            operationQueue.clear()
            operationInFlight = false
        }
    }

    // ── GATT callback ─────────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected — discovering services")
                    reconnectAttempts = 0
                    _connectionState.value = ConnectionState.CONNECTED
                    try {
                        gatt.discoverServices()
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Missing BLE permission for discoverServices()", e)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected (status=$status)")
                    clearOperationQueue()
                    vibrationChar = null

                    val unexpectedDrop = status != BluetoothGatt.GATT_SUCCESS && !userInitiatedDisconnect
                    if (unexpectedDrop && reconnectAttempts < Constants.BLE_MAX_RETRY_ATTEMPTS) {
                        reconnectAttempts++
                        Log.w(TAG, "Unexpected disconnect — reconnect attempt $reconnectAttempts/${Constants.BLE_MAX_RETRY_ATTEMPTS}")
                        _connectionState.value = ConnectionState.CONNECTING
                        mainHandler.postDelayed({
                            try {
                                gatt.connect()
                            } catch (e: SecurityException) {
                                Log.e(TAG, "Missing BLE permission for reconnect", e)
                                _connectionState.value = ConnectionState.DISCONNECTED
                            }
                        }, Constants.BLE_RETRY_DELAY_MS)
                    } else {
                        if (unexpectedDrop) {
                            Log.w(TAG, "Giving up after $reconnectAttempts reconnect attempts")
                        }
                        _connectionState.value = ConnectionState.DISCONNECTED
                        this@BleManager.gatt = null
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                return
            }

            val service = gatt.getService(UUID.fromString(Constants.BLE_SERVICE_UUID))
            if (service == null) {
                Log.e(TAG, "Glove service not found")
                return
            }

            // Cache vibration characteristic for outgoing writes
            vibrationChar = service.getCharacteristic(
                UUID.fromString(Constants.BLE_VIBRATION_CHAR_UUID)
            )

            // Subscribe to incoming tap events, then battery — queued so the
            // second descriptor write waits for the first's onDescriptorWrite
            // callback instead of racing it.
            service.getCharacteristic(UUID.fromString(Constants.BLE_TAP_CHAR_UUID))
                ?.let { enqueueEnableNotifications(gatt, it) }

            service.getCharacteristic(UUID.fromString(Constants.BLE_BATTERY_LOW_CHAR_UUID))
                ?.let { enqueueEnableNotifications(gatt, it) }

            Log.d(TAG, "Services ready — glove operational")
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Descriptor write failed for ${descriptor.characteristic.uuid}: status=$status")
            }
            onGattOperationComplete()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Characteristic write failed for ${characteristic.uuid}: status=$status")
            }
            onGattOperationComplete()
        }

        // API < 33
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            handleIncoming(characteristic.uuid, characteristic.value)
        }

        // API 33+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncoming(characteristic.uuid, value)
        }
    }

    // ── Incoming data handler ─────────────────────────────────────────────────

    private fun handleIncoming(uuid: UUID, value: ByteArray) {
        when (uuid) {
            UUID.fromString(Constants.BLE_TAP_CHAR_UUID) -> {
                if (value.size >= 6) {
                    // Convert 6-byte payload → BooleanArray (non-zero = tapped)
                    val pattern = BooleanArray(6) { i -> value[i] != 0.toByte() }
                    mainHandler.post { onTapReceived?.invoke(pattern) }
                }
            }
            UUID.fromString(Constants.BLE_BATTERY_LOW_CHAR_UUID) -> {
                if (value.isNotEmpty()) {
                    _isBatteryLow.value = value[0] != 0.toByte()
                }
            }
        }
    }

    // ── Outgoing vibration ────────────────────────────────────────────────────

    /**
     * Sends a 6-byte vibration payload to the glove.
     * Build this with BrailleEngine.buildVibrationPayload(letter).
     *
     * Queued behind any in-flight GATT operation (e.g. a subscription still
     * being set up right after connecting) rather than racing it.
     *
     * @return true if the write was successfully queued/initiated.
     */
    fun sendVibration(payload: ByteArray): Boolean {
        val char = vibrationChar ?: run {
            Log.w(TAG, "Vibration characteristic not ready")
            return false
        }
        val g = gatt ?: return false

        enqueueGattOperation {
            try {
                // Match whatever write type the firmware's characteristic
                // actually advertises, rather than assuming "with response".
                char.writeType = if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }
                @Suppress("DEPRECATION")
                char.value = payload
                @Suppress("DEPRECATION")
                val success = g.writeCharacteristic(char)
                if (!success) {
                    Log.w(TAG, "writeCharacteristic returned false")
                    onGattOperationComplete()
                }
                // On success, onCharacteristicWrite() will advance the queue.
            } catch (e: SecurityException) {
                Log.e(TAG, "Missing BLE permission for writeCharacteristic()", e)
                onGattOperationComplete()
            }
        }
        return true
    }

    // ── Notification helper ───────────────────────────────────────────────────

    private fun enqueueEnableNotifications(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        enqueueGattOperation {
            try {
                gatt.setCharacteristicNotification(char, true)
                val descriptor = char.getDescriptor(UUID.fromString(Constants.BLE_CCCD_UUID))
                if (descriptor == null) {
                    Log.w(TAG, "No CCCD descriptor on ${char.uuid} — notifications not enabled")
                    onGattOperationComplete()
                    return@enqueueGattOperation
                }
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                val success = gatt.writeDescriptor(descriptor)
                if (!success) {
                    Log.w(TAG, "writeDescriptor returned false for ${char.uuid}")
                    onGattOperationComplete()
                }
                // On success, onDescriptorWrite() will advance the queue.
            } catch (e: SecurityException) {
                Log.e(TAG, "Missing BLE permission for writeDescriptor()", e)
                onGattOperationComplete()
            }
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    fun destroy() {
        mainHandler.removeCallbacksAndMessages(null)
        disconnect()
    }

    // ── Tap-answer collection ─────────────────────────────────────────────────

    /**
     * Waits for the child to tap out a full multi-dot answer on the glove.
     *
     * The glove firmware notifies with whatever switches are *currently*
     * held down every time that state changes (see checkTaps() in the
     * firmware and /docs/BLE_GATT_CONTRACT.md) — it does NOT wait for a
     * "gesture complete" event, and it does NOT report which dots were
     * pressed over time. So a child tapping dot 1, letting go, then
     * tapping dot 2 produces a stream of separate notifications like
     * [1,0,0,0,0,0] → [0,0,0,0,0,0] → [0,1,0,0,0,0] — each just a
     * snapshot of what's held down at that instant, never the combined
     * "1 and 2" the letter actually needs.
     *
     * This function fixes that by treating tap notifications as a stream
     * of individual dot-press events rather than a single final answer:
     * it OR's every dot that goes from not-pressed to pressed into a
     * running pattern, so dots can be tapped one at a time instead of
     * needing to be held simultaneously. It only finalizes the answer
     * once the child pauses for [settleMs] after their most recent tap
     * (a natural "I'm done" signal), rather than grabbing the very first
     * notification that arrives.
     *
     * @param maxWaitMs total time to wait before giving up if the child
     *   never taps anything at all.
     * @param settleMs  how long to wait, after the last new dot press,
     *   before locking in the accumulated pattern as the final answer.
     * @param onProgress optional callback invoked (main thread) with the
     *   accumulated-so-far pattern each time a new dot is added, so the
     *   UI can give the child live feedback (e.g. "dot 1 ✓, keep going").
     * @return the accumulated 6-dot pattern, or null if no dot was
     *   pressed at all within [maxWaitMs].
     */
    suspend fun collectTapAnswer(
        maxWaitMs: Long = Constants.TAP_ANSWER_MAX_WAIT_MS,
        settleMs: Long = Constants.TAP_ANSWER_SETTLE_MS,
        onProgress: ((BooleanArray) -> Unit)? = null
    ): BooleanArray? {
        val accumulated = BooleanArray(6)
        var hasAnyTap = false
        var lastChangeTime = System.currentTimeMillis()
        val startTime = lastChangeTime

        onTapReceived = { pattern ->
            var changed = false
            for (i in 0 until 6) {
                if (pattern[i] && !accumulated[i]) {
                    accumulated[i] = true
                    changed = true
                }
            }
            if (changed) {
                hasAnyTap = true
                lastChangeTime = System.currentTimeMillis()
                onProgress?.invoke(accumulated.copyOf())
            }
        }

        try {
            while (true) {
                val now = System.currentTimeMillis()
                if (now - startTime >= maxWaitMs) break
                if (hasAnyTap && now - lastChangeTime >= settleMs) break
                kotlinx.coroutines.delay(50)
            }
        } finally {
            onTapReceived = null
        }

        return if (hasAnyTap) accumulated else null
    }
}
