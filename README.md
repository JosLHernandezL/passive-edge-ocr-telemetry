# Passive Edge-OCR & Voice Telemetry Engine (AsistenteDidi)

![Java](https://img.shields.io/badge/Language-Java-orange.svg)
![Platform](https://img.shields.io/badge/Platform-Android%20SDK-green.svg)
![ML Kit](https://img.shields.io/badge/AI-Google%20ML%20Kit%20(Offline)-blue.svg)
![Architecture](https://img.shields.io/badge/Architecture-Edge%20Computing%20%2F%20Closed--Loop-lightgrey.svg)

A native Android background telemetry application written in **Java** that performs real-time, offline optical character recognition (**Edge OCR**) and rule-based decision-making, delivering synthesized voice alerts (`TextToSpeech`) directly to a Bluetooth helmet intercom for hands-free motorcycle operation.

---

## Engineering Overview & Problem Statement

Evaluating incoming ride-hailing offers while riding a motorcycle introduces severe visual distraction and touch-interaction latency. Furthermore, standard UI-tree scraping fails on custom graphical overlays, and uncoordinated audio playback over Bluetooth suffers from channel-establishment clipping.

Applying principles from **Control Systems and IT/OT Edge Computing**, this application implements a non-intrusive, closed-loop sensing-and-actuation pipeline:
1. **Sensing:** Periodic cropped screen capture via `AccessibilityService` (`takeScreenshot`), even with the display turned off.
2. **Edge Signal Processing:** On-device text recognition using **Google ML Kit** (zero cloud latency) paired with **RegEx** parsing to extract fare, total distance (pickup + drop-off), and passenger trip history.
3. **Rule Evaluation:** Real-time filtering against user-defined operational thresholds stored in `SharedPreferences` (minimum fare, minimum currency-per-kilometer ratio, and minimum passenger trip count).
4. **Deterministic Actuation:** Voice synthesis (`TextToSpeech`) routed to a Bluetooth helmet intercom, synchronized with an automatic screen lock (`GLOBAL_ACTION_LOCK_SCREEN`) triggered via `UtteranceProgressListener` (`onStart + 200 ms`) to prevent audio lag and eliminate accidental touch lockouts.

---

## System Architecture

```text
+-------------------------------------------------------------------------+
|                    Watchdog Polling Loop (Handler)                      |
+------------------------------------+------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|  AccessibilityService: Cropped takeScreenshot() (Background / Off-Screen)|
+------------------------------------+------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|  Google ML Kit Text Recognition (100% Offline Edge Inference)           |
+------------------------------------+------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|  RegEx Extraction & Normalization (Fare | Total Km | Passenger Trips)   |
+------------------------------------+------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|  Business Rule Engine (SharedPreferences Thresholds)                    |
+------------------------------------+------------------------------------+
                                     | [Thresholds Met]
                                     v
+-------------------------------------------------------------------------+
|  TextToSpeech Output ---> Bluetooth Helmet Intercom (Y10 2X)            |
|        |                                                                |
|        +--> UtteranceProgressListener (onStart + 200 ms)                |
|                   |                                                     |
|                   +--> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)   |
+-------------------------------------------------------------------------+
```

---

## Key Technical Highlights

* **Zero-Touch & Policy-Compliant Passive Design:** Strictly read-only execution; simulates zero screen taps or automated UI gestures.
* **Synchronized Hardware Screen Lock (`onStart + 200 ms`):** Resolves the race condition between Bluetooth audio buffer engagement and Android power states, ensuring crisp voice delivery while clearing touch-layer contention before manual acceptance.
* **Low-Overhead Edge Execution:** Cropping bitmaps prior to ML Kit inference minimizes CPU/RAM footprint and thermal throttling during continuous multi-hour field operation.
* **Persistent Threshold Configuration:** Dynamic parameter tuning (minimum fare, COP/km, trip count) managed via Android `SharedPreferences`.

---

## Hardware & Validation Environment

* **Device:** Motorola Moto G60 (Android)
* **Audio Hardware:** Y10 2X Motorcycle Helmet Bluetooth Intercom
* **Core Stack:** Java, Android `AccessibilityService`, `Handler`/`Looper`, Google ML Kit Text Recognition, Android `TextToSpeech` (`UtteranceProgressListener`), `SharedPreferences`.

---

## Roadmap (v2.0)

* **Sample-and-Hold Temporal Filter:** Implement offer-level state latching (`fare + distance` key) to eliminate transient `-1` OCR readings during card entry/exit animations.
* **Hybrid Event-Driven Triggering:** Combine targeted accessibility window events with adaptive polling intervals to further reduce battery consumption.
* **Local SQLite Telemetry & BI Pipeline:** Persist captured offer metrics locally (`SQLite`) for downstream SQL/Power BI analysis comparing offered vs. actual GPS-tracked kilometers.
