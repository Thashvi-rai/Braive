# Braive — Braille Learning Glove

Braive teaches children Grade-1 (letter-level) English Braille through **touch, sound and repetition**.
A custom **ESP32 haptic glove** vibrates the dot pattern of a letter on the child's fingertips,
and an **Android app** asks them to say the letter back or tap the pattern on the glove's switches —
adapting what it asks next to how that child is actually doing.

Everything runs **offline**: no server, no account, no cloud.

## How it works

| Part | What it does |
|------|--------------|
| **Glove** (ESP32) | 6 vibration motors + 6 tap switches laid out as a Braille cell. Talks to the phone over Bluetooth LE. |
| **App** (Android, Kotlin) | Learning / Practice / Test modes, speech (TTS + STT), adaptive question engine, progress saved on-device. |

### The three modes
- **Learning** – free exploration. Pick any letter A–Z; the glove vibrates it and the app speaks it.
- **Practice** – gamified, adaptive quiz with lives, levels and stars. Weak letters appear more often.
- **Test** – spelling assessment; the child taps out a word letter by letter.

## Repository layout

```
app/        Android application (Kotlin, MVVM, Room, ViewBinding)
firmware/   ESP32 Arduino sketch for the glove
docs/       BLE_GATT_CONTRACT.md – the phone <-> glove protocol (read this first!)
licenses/   Third-party licences (Atkinson Hyperlegible font, OFL)
```

Inside `app/src/main/java/com/example/brailleglove/`:

```
ble/        BleManager – scan, connect, GATT operation queue, tap collection
braille/    BrailleAlphabet + BrailleEngine – dot patterns, validation, speech matching
data/       Room database, DAOs, models, repository, adaptive Question generator
speech/     SpeechManager – text-to-speech & speech recognition
ui/         home / learning / practice / test  (Fragment + ViewModel each)
audio/      Sound effects        utils/   Constants, animations, ViewModelFactory
```

## Getting started

### Android app
**Requirements:** Android Studio (recent), JDK 17, an Android phone (API 24+) with Bluetooth.
Use a **physical phone** — the emulator can't do real BLE.

1. Clone the repo and open the folder in Android Studio.
2. Let Gradle sync (Android Studio creates your own `local.properties` automatically — it is git-ignored).
3. Plug in your phone (USB debugging on) and press **Run**.
4. Grant the Bluetooth, location and microphone permissions when asked.

Command line: `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`).

### Glove firmware
**Requirements:** Arduino IDE with the **ESP32 board package** (the BLE libraries ship with it).

1. Open `firmware/braille_glove_firmware.ino`.
2. Check `swPins[]` and `motorPins[]` match **your** wiring.
3. Select your ESP32 board and upload.

> The service/characteristic UUIDs in the firmware **must match** `Constants.kt` exactly.
> Any change to the protocol must be made on both sides and documented in `docs/BLE_GATT_CONTRACT.md`.

## Tech stack
Kotlin · AndroidX (ViewModel, LiveData, Navigation, Room) · Coroutines · Android TTS/SpeechRecognizer ·
Bluetooth LE (GATT) · ESP32 + Arduino framework · Atkinson Hyperlegible font (accessibility)

Min SDK 24 · Target SDK 36 · Java/Kotlin JVM target 17

## Contributing
Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

## Team
<!-- Add names / roles here -->
- 

## Licence
<!-- Choose a licence (e.g. MIT) and add a LICENSE file. Until then, all rights are reserved by the authors. -->
The bundled Atkinson Hyperlegible font is used under the SIL Open Font License (see `licenses/`).
