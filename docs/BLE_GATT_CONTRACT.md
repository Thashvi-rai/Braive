# BLE GATT Contract — Braive ↔ Android App

> A reference implementation of the firmware side now exists at
> `/firmware/braille_glove_firmware.ino` — it implements everything
> below using the real placeholder UUIDs already in `Constants.kt`, so
> the Android app needs no changes.

The Android app (`BleManager.kt`) is a complete BLE **client**. There is no
ESP32 **firmware** in this repo yet — this document is the spec the
firmware needs to implement so the two sides actually work together.
Every UUID below is currently a **placeholder** in `Constants.kt`
(`12345678-1234-1234-1234-123456789abc` and friends) and should be
replaced with real, randomly-generated UUIDs on both sides before pairing
with real hardware — swap them in `Constants.kt` and keep the firmware in
sync.

## Service

One primary GATT service, advertised so the app's scan filter (which
filters by this exact service UUID) can find it.

| Constant | Current placeholder |
|---|---|
| `Constants.BLE_SERVICE_UUID` | `12345678-1234-1234-1234-123456789abc` |

## Characteristics

### 1. Vibration (app → glove)

| | |
|---|---|
| UUID constant | `Constants.BLE_VIBRATION_CHAR_UUID` |
| Direction | Android writes, ESP32 receives |
| Required property | `Write` **or** `Write Without Response` — the app now inspects the characteristic's advertised properties at runtime and matches whichever one the firmware declares, so either is fine, but pick one and be consistent |
| Payload | 6 bytes, one per braille dot |
| Byte format | `byte[i] = 1` if dot `i+1` should raise, else `0`. Index 0 = dot 1, index 1 = dot 2, ... index 5 = dot 6 (standard 2-column layout: dots 1‑2‑3 down the left column, 4‑5‑6 down the right) |
| Example | Letter "B" (dots 1, 2) → `[1, 1, 0, 0, 0, 0]` |

### 2. Tap events (glove → app)

| | |
|---|---|
| UUID constant | `Constants.BLE_TAP_CHAR_UUID` |
| Direction | ESP32 notifies, Android receives |
| Required property | **Notify** (not Indicate — the app writes `ENABLE_NOTIFICATION_VALUE` to the CCCD, which only enables Notify. If the firmware characteristic is declared Indicate-only, this subscription will silently do nothing.) |
| Payload | 6 bytes, same format as vibration: non-zero = that dot was physically tapped |
| When to send | Once per completed tap gesture (e.g. debounced flex-sensor read), not a continuous stream |

### 3. Low-battery flag (glove → app)

| | |
|---|---|
| UUID constant | `Constants.BLE_BATTERY_LOW_CHAR_UUID` |
| Direction | ESP32 notifies, Android receives |
| Required property | **Notify** |
| Payload | 1 byte: `0` = OK, `1` = low. **Not a percentage** — deliberately kept to a single threshold rather than a full 0–100 reading, since there's no calibrated discharge curve for the current battery setup worth the extra wiring/firmware complexity |
| When to send | On connect, then periodically (e.g. every 30s, see `BATTERY_NOTIFY_INTERVAL_MS`) — the app has no polling/read path for this, only notify |
| Firmware status | Currently a placeholder that always reports `0` (OK) — see the `TODO` above `isBatteryLow()` in the firmware for what's needed to make this real (a voltage-divider circuit on a spare ADC pin) |

### 4. Client Characteristic Configuration Descriptor (CCCD)

Standard Bluetooth SIG descriptor, already correct — no firmware choice
here, just confirming: `00002902-0000-1000-8000-00805f9b34fb` on both the
tap and battery characteristics (needed for any characteristic that
supports Notify/Indicate).

## Things the Android client now handles that firmware doesn't need to

- **Reconnection**: if the connection drops unexpectedly, the app retries
  automatically (`Constants.BLE_MAX_RETRY_ATTEMPTS`, currently 3, spaced
  `Constants.BLE_RETRY_DELAY_MS` apart) before giving up and requiring the
  user to hit "Connect" again. Firmware doesn't need its own reconnect
  logic — just make sure the ESP32 keeps advertising when idle/disconnected
  so the app's next scan can find it again.
- **Operation ordering**: the client queues all GATT writes/descriptor
  writes and waits for each completion callback before sending the next,
  so firmware won't see two operations arrive back-to-back that need to be
  handled concurrently.

## Suggested next step

Generate three real UUIDs (e.g. `uuidgen` or Python's `uuid.uuid4()`) for
the service + vibration + tap characteristics, one more for battery, drop
them into both `Constants.kt` and the ESP32 sketch, and you have a
matched pair to test against real hardware.
