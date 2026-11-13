# SensOasis — Urban Hydration & Shade Companion (Madrid)

UPM Mobile Devices Programming (2026–2027) Course Project.  
Target terminal: **Motorola moto g35 5G** (`minSdk = 26`, `compileSdk = 37`, `targetSdk = 37`).

---

## 1. Architecture & Package Structure

The codebase is organized by clean package boundaries separating domain models, contracts, hardware sensors, data repositories, and UI screens.

| Package | Responsibility |
|---|---|
| `es.upm.sensoasis.domain.model` | Pure-Kotlin domain models (`Fountain`, `FountainReport`, `LightObservation`, `ProximityState`, `SpeechState`, `MqttTopics`, `Constants`, `LightFormatting`, etc.). **Zero `android.*` imports.** |
| `es.upm.sensoasis.domain.contract` | Core interfaces between modules (`OpenDataRepository`, `MqttRepository`, `ReportUseCase`, `LightSensorController`, `ProximityStateProvider`, `PrivateSpeechController`, `LocationProvider`, `DistanceCalculator`). |
| `es.upm.sensoasis.fake` | Deterministic in-memory Fake implementations + `SampleData` (7 UI state presets for Compose `@Preview` and local testing). |
| `es.upm.sensoasis.di` | Manual dependency container (`AppContainer`) with per-module `useFake*` switches and unique installation UUID `clientId`. |
| `es.upm.sensoasis.viewmodel` | Single Source of Truth state (`SensOasisUiState`), `UiAction`, `UiEffect`, and `SensOasisViewModel`. |
| `es.upm.sensoasis.location` | `HaversineDistanceCalculator` and `FusedLocationProvider` implementation (Madrid Puerta del Sol fallback when permission is denied or GPS is unavailable indoors). |
| `es.upm.sensoasis.sensor.light` | Ambient light sensor (`TYPE_LIGHT`) short sampling window, lux-to-`LightBand` classification (`LOW` / `MODERATE` / `BRIGHT`), and cross-sensor proximity validation (`FAR` required). |
| `es.upm.sensoasis.sensor.proximity` | Proximity sensor (`TYPE_PROXIMITY`) abstraction (`NEAR` / `FAR` / `UNKNOWN`). |
| `es.upm.sensoasis.speech` | Private earpiece/near-ear speech playback (`PrivateSpeechController`, `SpeechSummaryUseCase`, `SpeechController`). |
| `es.upm.sensoasis.data.opendata` | Madrid Open Data (`datos.madrid.es`) fountain dataset loading, parsing, and bundled offline asset fallback. |
| `es.upm.sensoasis.data.mqtt` | Real MQTT client (MQTTastic / `org.meshtastic:mqtt-client-*`), JSON payload serialization, retained report publishing (`QoS 1`, `retain = true`) to `sensoasis/fountains/{fountainId}/reports`, and wildcard subscription to `sensoasis/fountains/+/reports` (see `MqttTopics`). |
| `es.upm.sensoasis.usecase` | Use-case implementations bridging sensor capture, speech summary building, and MQTT report publication. |
| `es.upm.sensoasis.ui.*` | Jetpack Compose screens (`FountainListScreen`, `FountainDetailScreen`, `ReportScreen`), navigation host, and Material 3 theme. |

---

## 2. Core Architectural Rules

1. **Pure Domain Layer**: Files under `domain/model/` and `domain/contract/` must **never** import `android.*`.
2. **Contract Consistency**: Modifying existing signatures in `domain/` requires updating the corresponding `fake/` implementations and unit tests together (`CODEOWNERS` protects `domain/`, `viewmodel/`, and `di/`).
3. **Single Lifecycle Owner for Proximity Sensor**:
   - **Only** `SensOasisViewModel` calls `ProximityStateProvider.start()` and `stop()` (active whenever `reportScreenActive || privateSpeechEnabled`).
   - `LightSensorController` and `PrivateSpeechController` must **only** read/collect `proximityStateProvider.state` and must **never** call `start()` or `stop()`.
4. **Report Submission Rules**:
   - Light capture is triggered explicitly by `UiAction.CaptureLight` (never automatically on screen entry or rotation).
   - A fountain status report can be submitted when `reportState` is `IDLE`, `READY`, `OCCLUDED`, or `UNAVAILABLE`. When `OCCLUDED` or `UNAVAILABLE`, `lightObservation` is submitted as `null`.
5. **Leaving `ReportScreen` & Screen Rotation Rule**:
   - Dispatch `UiAction.LeaveReportScreen` **only** when the user genuinely leaves the report screen (system back button via `BackHandler`, Cancel button, or navigating away).
   - **Never** dispatch `UiAction.LeaveReportScreen` unconditionally inside `DisposableEffect.onDispose`, because `onDispose` also triggers on screen rotation and would abort an in-progress ambient-light capture.
6. **Interaction Lock Formula**:
   - `interactionLocked = privateSpeechEnabled && (proximityState == NEAR || speechState == SPEAKING)`.
   - When `interactionLocked == true`, primary screen actions (`CaptureLight`, `SubmitReport`, `OpenWalkingRoute`) are disabled while `StopSpeech` / `EnablePrivateSpeech(false)` remain accessible.

---

## 3. Broker & Environment Configuration (`local.properties`)

You can configure the MQTT broker host and port in your untracked `local.properties` file without modifying tracked source code:

```properties
sdk.dir=C\:\\Users\\<your-user>\\AppData\\Local\\Android\\Sdk

# Default public test broker:
MQTT_HOST=test.mosquitto.org
MQTT_PORT=1883

# Course Ubuntu VirtualBox VM Mosquitto broker (Host-Only Adapter):
# MQTT_HOST=192.168.56.1
# MQTT_PORT=1883
```

These values (or lowercase `mqtt.host` / `mqtt.port`) are injected into `BuildConfig.MQTT_HOST`, `BuildConfig.MQTT_PORT`, `BuildConfig.MQTT_WS_PORT`, `BuildConfig.MQTT_TRANSPORT`, and `BuildConfig.MQTT_TOPIC_ROOT` at build time and read by `AppContainer.mqttConfig`.

---

## 4. Switching Between Fakes and Real Modules (`AppContainer.kt`)

During development, `AppContainer` (`app/src/main/java/es/upm/sensoasis/di/AppContainer.kt`) provides independent module switches (`useFake*`):

- `useFakeOpenData = true`
- `useFakeMqtt = true`
- `useFakeLightSensor = false` (uses real `RealLightSensorController` with `TYPE_LIGHT` + `ProximityStateProvider` cross-check)
- `useFakeProximity = true`
- `useFakeSpeech = true`
- `useFakeLocation = false` (uses real `FusedLocationProvider` with Puerta del Sol fallback)

When plugging in a real module:
1. Implement the real class inside the corresponding package.
2. In `AppContainer.kt`, wire the `else` branch of the module property to instantiate the real class and flip its `useFake*` flag to `false`.

---

## 5. Sample Payloads & Bundled Assets

- **Open Data Bundled Fallback**: `app/src/main/assets/samples/fountains_sample.json` (real Madrid drinking fountains around Puerta del Sol, Retiro, Callao, Plaza Mayor, Moncloa, and Cibeles).
- **MQTT Test JSON Payloads**: `app/src/test/resources/mqtt/`
  - `report_valid.json` — Valid report with ambient light observation (`ambientLux = 3200.0`, `MODERATE`, `VALID`).
  - `report_null_light.json` — Valid report submitted without light observation (`lightObservation = null`).
  - `report_occluded.json` — Report payload with occluded light observation (`quality = "OCCLUDED"`, `ambientLux = null`, `lightBand = null`).
  - `report_expired.json` — Historical report past the 20-minute TTL (`expiresAt` in the past).

### Report wire contract and live repositories

Reports are UTF-8 JSON with `fountainId`, `status`, optional `lightObservation`, `observedAt`, and `expiresAt` (ISO-8601 UTC timestamps). The optional observation contains `ambientLux`, `lightBand`, `quality`, and its own `observedAt`. A missing or `null` observation is a valid fountain status report. The canonical null-light example is `app/src/test/resources/mqtt/report_null_light.json`.

- Publish topic: `sensoasis/fountains/{fountainId}/reports` (QoS 1, retained).
- Community subscription: `sensoasis/fountains/+/reports`.
- MQTT config continues to come from `local.properties`; `AppContainer` now enables the real MQTT and open-data modules by default. Set `useFakeMqtt` / `useFakeOpenData` to `true` for local deterministic UI runs.
- The MQTT connection always uses the injected `MqttConfig` host, port, transport, client ID, and topic root, so the teacher VM can be selected through `MQTT_HOST` / `MQTT_PORT` in `local.properties`. No public broker address is hardcoded in `RealMqttRepository`.
- Madrid fountain JSON is fetched from the official `300051-0-fuentes` download without credentials. Its uppercase dataset fields, WGS84 coordinates, and ETRS89 UTM fallback are parsed. On timeout, HTTP/network error, empty response, or invalid remote JSON, `RealOpenDataRepository` parses the bundled `assets/samples/fountains_sample.json` automatically.
- Air quality uses the Open-Meteo Air Quality API `current=european_aqi`; it requires no account or API key. Parsed values are exposed as `AirQualitySummary` / `AirQuality`.
- Community reports are cached locally per fountain, restored on app start, and filtered against `expiresAt`; MQTT 5 message expiry removes stale retained reports from a compatible broker.
- The MQTTastic client negotiates MQTT 5 with MQTT 3.1.1 fallback. Broker-side retained-message expiry uses MQTT 5; clients still reject expired reports from either protocol.
- `FountainReportJsonTest` exercises valid, null-light, occluded, and expired wire payloads. The broker integration still requires two devices (or an independent MQTT publisher/subscriber) configured for the same reachable broker and topic root.

---

## 6. Module Verification Checklist

| Module | Verification Criteria |
|---|---|
| **Ambient Light (`sensor.light`)** | Unit tests cover median calculation, single-reading window, zero-reading timeout (`UNAVAILABLE`), and 3-band classification (`LOW`, `MODERATE`, `BRIGHT`). On physical moto g35 5G, covering the top sensor yields `OCCLUDED` and uncovered capture yields `VALID`. |
| **Proximity & Speech (`sensor.proximity`, `speech`)** | Unit tests cover NEAR (2.0s) and FAR (0.4s) debounce logic and the `WAITING_FOR_NEAR -> SPEAKING -> WAITING_FOR_NEAR` state loop. On physical moto g35 5G, holding near ear for 2s starts speech, moving away stops speech immediately, and subsequent near-ear gestures re-trigger cleanly. |
| **Open Data & MQTT (`data.opendata`, `data.mqtt`)** | Unit tests verify parsing of all 4 MQTT JSON payloads (`report_valid.json`, `report_null_light.json`, `report_occluded.json`, `report_expired.json`) and fountain dataset JSON. Connects to Mosquitto broker with QoS 1 + `retain = true`, filters expired retained reports on ingress, and passes two-device publish/subscribe test. |
| **Compose UI & Navigation (`ui.*`)** | All 7 `SampleData` UI state presets render in `@Preview`. `ReportScreen` requires **zero scrolling** in both portrait and landscape orientations on moto g35 5G, and primary buttons are disabled whenever `interactionLocked == true`. |

---

## 7. Build & Test Commands

```powershell
# Run unit tests
.\gradlew.bat testDebugUnitTest

# Assemble debug APK
.\gradlew.bat assembleDebug
```
