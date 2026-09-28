/*
  Braille Glove — ESP32 BLE firmware (6-dot version)
  ----------------------------------------------------------------
  Replaces the earlier WiFi/TCP-to-Python prototype with a direct
  Bluetooth LE connection to the Android app — no PC/server needed.

  This advertises the exact GATT service the Android app already
  expects (see Constants.kt / docs/BLE_GATT_CONTRACT.md) — the app
  needs ZERO changes to talk to this.

  Libraries used (BLEDevice, BLEServer, BLEUtils, BLE2902) ship with
  the standard Arduino-ESP32 board package — no extra library install.

  ⚠ PIN NUMBERS BELOW ARE A STARTING SUGGESTION, NOT YOUR WIRING.
  Check them against how you actually wire the 2 new switches/motors
  and adjust swPins[]/motorPins[] to match before flashing.
*/

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// ── GATT UUIDs — MUST match Constants.kt in the Android app exactly ──────
#define SERVICE_UUID        "12345678-1234-1234-1234-123456789abc"
#define VIBRATION_CHAR_UUID "12345678-1234-1234-1234-123456789abd"
#define TAP_CHAR_UUID       "12345678-1234-1234-1234-123456789abe"
#define BATTERY_LOW_CHAR_UUID "12345678-1234-1234-1234-123456789abf"

// ── Hardware pins ──────────────────────────────────────────────────────
// Index order matters: [0]=dot1 [1]=dot2 [2]=dot3 [3]=dot4 [4]=dot5 [5]=dot6
// (left column top-to-bottom, then right column top-to-bottom — same
// convention the Android app's dot grid and BrailleAlphabet.kt use).
// First 4 of each kept from your original sketch; last 2 are new.
const int swPins[6]    = {13, 12, 14, 27, 26, 25};
const int motorPins[6] = {18, 19, 21, 22, 23, 32};

const unsigned long DEBOUNCE_MS = 25;        // settle time for switch reads
const unsigned long VIBRATION_HOLD_MS = 1000; // same hold time as your original sketch
const unsigned long BATTERY_NOTIFY_INTERVAL_MS = 30000; // every 30s

// ── Braille table: bit i (0-indexed) = dot (i+1). Full Grade-1 A-Z. ─────
// (Your original table's encoding for A-J was correct — this just adds
// the K-Z entries using the same convention, now with all 6 bits used.)
struct Map { char letter; byte pattern; };
const Map table[] = {
  {'A', 0b000001},  // dots 1
  {'B', 0b000011},  // dots 1,2
  {'C', 0b001001},  // dots 1,4
  {'D', 0b011001},  // dots 1,4,5
  {'E', 0b010001},  // dots 1,5
  {'F', 0b001011},  // dots 1,2,4
  {'G', 0b011011},  // dots 1,2,4,5
  {'H', 0b010011},  // dots 1,2,5
  {'I', 0b001010},  // dots 2,4
  {'J', 0b011010},  // dots 2,4,5
  {'K', 0b000101},  // dots 1,3
  {'L', 0b000111},  // dots 1,2,3
  {'M', 0b001101},  // dots 1,3,4
  {'N', 0b011101},  // dots 1,3,4,5
  {'O', 0b010101},  // dots 1,3,5
  {'P', 0b001111},  // dots 1,2,3,4
  {'Q', 0b011111},  // dots 1,2,3,4,5
  {'R', 0b010111},  // dots 1,2,3,5
  {'S', 0b001110},  // dots 2,3,4
  {'T', 0b011110},  // dots 2,3,4,5
  {'U', 0b100101},  // dots 1,3,6
  {'V', 0b100111},  // dots 1,2,3,6
  {'W', 0b111010},  // dots 2,4,5,6
  {'X', 0b101101},  // dots 1,3,4,6
  {'Y', 0b111101},  // dots 1,3,4,5,6
  {'Z', 0b110101},  // dots 1,3,5,6
};
const int sizeTable = sizeof(table) / sizeof(table[0]);
// NOTE: this table isn't actually used by the BLE read/write paths below —
// the app sends/receives raw 6-byte dot arrays directly, not letters. It's
// kept here in case you want it for standalone testing (e.g. Serial Monitor
// input) without the app connected. See testFromSerial() at the bottom.

// ── BLE globals ───────────────────────────────────────────────────────
BLEServer* pServer = nullptr;
BLECharacteristic* pVibrationChar = nullptr;
BLECharacteristic* pTapChar = nullptr;
BLECharacteristic* pBatteryLowChar = nullptr;
bool deviceConnected = false;

// ── Connection lifecycle ─────────────────────────────────────────────
class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* server) override {
    deviceConnected = true;
    Serial.println("Phone connected");
  }
  void onDisconnect(BLEServer* server) override {
    deviceConnected = false;
    Serial.println("Phone disconnected — resuming advertising");
    delay(200); // brief pause before restarting advertising — avoids a
                // known ESP32 BLE stack race right after disconnect
    server->getAdvertising()->start();
  }
};

// ── Vibration characteristic: app writes 6 bytes, we drive the motors ──
class VibrationCallback : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* characteristic) override {
    std::string value = characteristic->getValue();
    if (value.length() < 6) return; // malformed payload, ignore

    for (int i = 0; i < 6; i++) {
      digitalWrite(motorPins[i], value[i] != 0 ? HIGH : LOW);
    }
    delay(VIBRATION_HOLD_MS); // blocking, same behavior as your original
                              // sketch's delay(1000) — simplest correct
                              // version; can be made non-blocking with a
                              // millis() timer later if it ever needs to
                              // run alongside something else
    for (int i = 0; i < 6; i++) {
      digitalWrite(motorPins[i], LOW);
    }
  }
};

// ── Switch reading + debounce + tap notify ──────────────────────────
byte readSwitches() {
  byte value = 0;
  for (int i = 0; i < 6; i++) {
    if (!digitalRead(swPins[i])) value |= (1 << i); // INPUT_PULLUP: pressed = LOW
  }
  return value;
}

void sendTapPattern(byte pattern) {
  if (!deviceConnected) return;
  uint8_t payload[6];
  for (int i = 0; i < 6; i++) payload[i] = (pattern >> i) & 1;
  pTapChar->setValue(payload, 6);
  pTapChar->notify();
}

void checkTaps() {
  static byte lastSent = 0xFF;      // 0xFF never occurs (max real value is 63)
  static byte lastRaw = 0xFF;
  static unsigned long lastChangeTime = 0;
  // Sticky OR of every bit seen high since the last notify sent. A plain
  // "wait until stable" debounce can swallow a fast, light single-dot tap
  // entirely: if dot 1 is pressed and released again faster than
  // DEBOUNCE_MS, raw bounces back to the idle baseline (0) before the
  // window ever closes, and since that matches lastSent, nothing gets
  // sent at all -- the tap just vanishes. A two-dot combo rarely hits
  // this because coordinating two fingers naturally takes longer than
  // DEBOUNCE_MS, so it reliably clears the window -- which is why only
  // single-dot letters (just A) were the ones going missing.
  static byte seenSincePress = 0;

  byte raw = readSwitches();
  if (raw != lastRaw) {
    lastRaw = raw;
    lastChangeTime = millis();
    Serial.printf("[tap] raw switch state changed: 0x%02X\n", raw); // bench diagnostic
  }
  seenSincePress |= raw;

  // Only commit once the reading has been stable for DEBOUNCE_MS -- but
  // send the STICKY value, not the instantaneous one, so a dot that came
  // and went inside the debounce window is still reported.
  if (seenSincePress != lastSent && (millis() - lastChangeTime) >= DEBOUNCE_MS) {
    lastSent = seenSincePress;
    sendTapPattern(seenSincePress);
    seenSincePress = raw; // carry forward whatever's still actually held down
  }
}

// ── Battery notify: simple low/OK flag, not a percentage ────────────
// Deliberately not a percentage — two non-rechargeable-in-place BL-5C
// cells don't have a clean, calibratable discharge curve worth the
// wiring complexity for this project, so this is just "is it getting
// low, yes or no."
//
// TODO: once a voltage-divider circuit exists on a spare input-only ADC
// pin (e.g. GPIO34), replace isBatteryLow() below with a real read:
//   float voltage = analogRead(BATTERY_ADC_PIN) * ADC_TO_VOLTS;
//   return voltage < LOW_BATTERY_THRESHOLD_V;
// LOW_BATTERY_THRESHOLD_V depends on how the two BL-5C cells are wired:
//   parallel (~3.7V nominal)  -> threshold around 3.4V
//   series   (~7.4V nominal)  -> threshold around 6.8V, and the ESP32
//                                 side needs a buck converter regardless
// Confirm the wiring before wiring this up.
bool isBatteryLow() {
  return false; // placeholder: always reports OK until real sensing exists
}

void checkBattery() {
  static unsigned long lastSent = 0;
  if (millis() - lastSent < BATTERY_NOTIFY_INTERVAL_MS) return;
  lastSent = millis();
  if (!deviceConnected) return;

  bool low = isBatteryLow();
  uint8_t flag = low ? 1 : 0;
  pBatteryLowChar->setValue(&flag, 1);
  pBatteryLowChar->notify();
}

// ── Setup ─────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);

  for (int i = 0; i < 6; i++) {
    pinMode(swPins[i], INPUT_PULLUP);
    pinMode(motorPins[i], OUTPUT);
    digitalWrite(motorPins[i], LOW);
  }

  BLEDevice::init("BrailleGlove");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService* pService = pServer->createService(SERVICE_UUID);

  pVibrationChar = pService->createCharacteristic(
      VIBRATION_CHAR_UUID, BLECharacteristic::PROPERTY_WRITE);
  pVibrationChar->setCallbacks(new VibrationCallback());

  pTapChar = pService->createCharacteristic(
      TAP_CHAR_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  pTapChar->addDescriptor(new BLE2902());

  pBatteryLowChar = pService->createCharacteristic(
      BATTERY_LOW_CHAR_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  pBatteryLowChar->addDescriptor(new BLE2902());

  pService->start();

  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->start();

  Serial.println("BLE advertising — waiting for phone...");
}

// ── Loop ──────────────────────────────────────────────────────────────
void loop() {
  checkTaps();
  checkBattery();
}

/* ── Optional: standalone bench test without the app connected ─────────
   Uncomment and call testFromSerial() from loop() if you want to type a
   letter into the Serial Monitor and feel it buzz, without needing BLE
   connected at all — handy for testing the motors/table independent of
   the phone side.

void testFromSerial() {
  if (!Serial.available()) return;
  char c = toupper(Serial.read());
  for (int i = 0; i < sizeTable; i++) {
    if (table[i].letter == c) {
      byte p = table[i].pattern;
      for (int j = 0; j < 6; j++) digitalWrite(motorPins[j], (p >> j) & 1);
      delay(VIBRATION_HOLD_MS);
      for (int j = 0; j < 6; j++) digitalWrite(motorPins[j], LOW);
      break;
    }
  }
}
*/
