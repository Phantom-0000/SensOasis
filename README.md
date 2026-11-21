# SensOasis — Urban Hydration & Comfort Companion (Madrid)

UPM Mobile Devices Programming (2026–2027) Course Project — **Team 9** (Tu Xianlong, Madiouni Nouha, Jasutkar Dhanashri, Martínez Anchuelo Adrian).  
Target terminal: **Motorola moto g35 5G** (`minSdk = 26`, `compileSdk = 37`, `targetSdk = 37`).

---

## 1. Architecture & Package Structure

The codebase is organized by clean package boundaries separating domain models, contracts, hardware sensors, data repositories, ViewModel state management, and stateless Compose UI screens.

| Package | Responsibility |
|---|---|
| `es.upm.sensoasis.domain.model` | Pure-Kotlin domain models (`Fountain`, `FountainReport`, `LightObservation`, `HourlyTemperatureSnapshot`, `AppLanguage`, `ProximityState`, `SpeechState`, `MqttTopics`, `Constants`, `LightFormatting`, etc.). **Zero `android.*` imports.** |
| `es.upm.sensoasis.domain.contract` | Core interfaces between modules (`OpenDataRepository`, `MqttRepository`, `ReportUseCase`, `LightSensorController`, `ProximityStateProvider`, `PrivateSpeechController`, `LocationProvider`, `DistanceCalculator`). |
| `es.upm.sensoasis.fake` | Deterministic in-memory Fake implementations + `SampleData` (7 UI state presets for Compose `@Preview` and local testing). |
| `es.upm.sensoasis.di` | Manual dependency container (`AppContainer`) with per-module `useFake*` switches and unique installation UUID `clientId`. |
| `es.upm.sensoasis.viewmodel` | Single Source of Truth state (`SensOasisUiState`), `UiAction`, `UiEffect`, and `SensOasisViewModel`. |
| `es.upm.sensoasis.location` | `HaversineDistanceCalculator` and `FusedLocationProvider` implementation (Madrid Puerta del Sol fallback when permission is denied or GPS is unavailable indoors). |
| `es.upm.sensoasis.sensor.light` | Ambient light sensor (`TYPE_LIGHT`) short sampling window, lux-to-`LightBand` classification (`LOW` / `MODERATE` / `BRIGHT`), and cross-sensor proximity validation (`FAR` required). |
| `es.upm.sensoasis.sensor.proximity` | Proximity sensor (`TYPE_PROXIMITY`) abstraction (`AndroidProximityStateProvider`: `NEAR` / `FAR` / `UNKNOWN`). |
| `es.upm.sensoasis.speech` | Trilingual (`EN` / `ES` / `CA`) private earpiece speech playback (`RealPrivateSpeechController`, `RealSpeechSummaryUseCase`, `AndroidSpeechController`) with voice-pack availability detection. |
| `es.upm.sensoasis.data.opendata` | Madrid Open Data (`datos.madrid.es`) fountain dataset loading, WGS84/UTM30 parsing (including `OPERATIVO`, `FUERA_DE_SERVICIO`, and `CERRADA_TEMPORALMENT` municipal statuses), bundled offline asset fallback, and Open-Meteo daytime hourly temperature (`HourlyWeatherDataSource`) with persistent offline cache. |
| `es.upm.sensoasis.data.mqtt` | Real MQTT client (MQTTastic / `org.meshtastic:mqtt-client-*`), JSON payload serialization, retained report publishing (`QoS 1`, `retain = true`) to `sensoasis/fountains/{fountainId}/reports`, and wildcard subscription to `sensoasis/fountains/+/reports` (see `MqttTopics`). |
| `es.upm.sensoasis.usecase` | Use-case implementations bridging sensor capture and MQTT report publication (`SubmitFountainReportUseCase`). |
| `es.upm.sensoasis.ui.i18n` | Instant trilingual localization (`EN` English / `ES` Español / `CA` Català) and senior text-size scaling (`A` 100% / `A+` 118%) persisted in `SharedPreferences`. |
| `es.upm.sensoasis.ui.components` | Stateless high-recognition graphical badges (`SeniorVisualBadges`), Vico `2.1.2` hourly outdoor temperature curve card, and `DebugSimulationBanner`. |
| `es.upm.sensoasis.ui.*` | Jetpack Compose screens (`HomeScreen` senior welcome hub, `FountainListScreen` with working-only filter, `FountainDetailScreen`, `ReportScreen`), single-Activity navigation host (`home -> list -> detail -> report`), and high-contrast Oasis Material 3 theme. |

---

## 2. Core Architectural Rules

1. **Pure Domain Layer**: Files under `domain/model/` and `domain/contract/` must **never** import `android.*`.
2. **Strict Layer Separation**: Compose UI components strictly consume `SensOasisUiState` from `SensOasisViewModel` and never invoke HTTP clients, MQTT clients, sensors, or JSON parsers directly.
3. **Single Lifecycle Owner for Proximity Sensor**:
   - **Only** `SensOasisViewModel` calls `ProximityStateProvider.start()` and `stop()` (active whenever `reportScreenActive || privateSpeechEnabled`).
   - `LightSensorController` and `PrivateSpeechController` must **only** read/collect `proximityStateProvider.state` and must **never** call `start()` or `stop()`.
4. **Report Submission Rules**:
   - Light capture is triggered explicitly by `UiAction.CaptureLight` (never automatically on screen entry or rotation).
   - A fountain status report can be submitted when `reportState` is `IDLE`, `READY`, `OCCLUDED`, or `UNAVAILABLE`. When `OCCLUDED` or `UNAVAILABLE`, `lightObservation` is submitted as `null`.
5. **Leaving `ReportScreen` & Screen Rotation Rule**:
   - Dispatch `UiAction.LeaveReportScreen` **only** when the user genuinely leaves the report screen (system back button via `BackHandler`, Back button, or navigating away).
   - **Never** dispatch `UiAction.LeaveReportScreen` unconditionally inside `DisposableEffect.onDispose`, because `onDispose` also triggers on screen rotation and would abort an in-progress ambient-light capture.
6. **Interaction Lock Formula**:
   - `interactionLocked = privateSpeechEnabled && speechState != ERROR && (proximityState == NEAR || speechState == SPEAKING)`.
   - When `interactionLocked == true`, primary screen actions (`CaptureLight`, `SubmitReport`, `OpenWalkingRoute`) are disabled while `StopSpeech` / `EnablePrivateSpeech(false)` remain accessible.

---

## 3. Broker & Environment Configuration (`local.properties`)

You can configure the MQTT broker host and port in your untracked `local.properties` file without modifying tracked source code:

```properties
sdk.dir=C\:\\Users\\<your-user>\\AppData\\Local\\Android\\Sdk

# Default public test broker:
MQTT_HOST=test.mosquitto.org
MQTT_PORT=1883

# Course Ubuntu VirtualBox VM Mosquitto broker:
# - From Android Emulator on the same PC (Host-Only / NAT): use the reachable VM IP
# - From physical moto g35 5G over Wi-Fi (set VirtualBox adapter to Bridged): use the VM LAN IP (e.g. 192.168.1.x)
# MQTT_HOST=192.168.1.50
# MQTT_PORT=1883
```

These values (or lowercase `mqtt.host` / `mqtt.port`) are injected into `BuildConfig.MQTT_HOST`, `BuildConfig.MQTT_PORT`, `BuildConfig.MQTT_WS_PORT`, `BuildConfig.MQTT_TRANSPORT`, and `BuildConfig.MQTT_TOPIC_ROOT` at build time and read by `AppContainer.mqttConfig`.

---

## 4. Switching Between Fakes and Real Modules (`AppContainer.kt`)

In the integrated release, all six switches in `AppContainer` (`app/src/main/java/es/upm/sensoasis/di/AppContainer.kt`) default to `false` so every real sensor, GPS, Open Data, TTS, and MQTT module is active out of the box:

- `useFakeOpenData = false` (uses `RealOpenDataRepository` with live Madrid municipal JSON + bundled fallback + cached Open-Meteo hourly forecast)
- `useFakeMqtt = false` (uses `RealMqttRepository` with MQTTastic TCP/WS client and 20-min local cache)
- `useFakeLightSensor = false` (uses `RealLightSensorController` with `TYPE_LIGHT` + `ProximityStateProvider` cross-check)
- `useFakeProximity = false` (uses `AndroidProximityStateProvider` with hardware `TYPE_PROXIMITY`)
- `useFakeSpeech = false` (uses `RealPrivateSpeechController` + trilingual `RealSpeechSummaryUseCase` + `AndroidSpeechController` earpiece routing)
- `useFakeLocation = false` (uses `FusedLocationProvider` with Puerta del Sol fallback)

Any switch can be set back to `true` individually for deterministic local simulation or UI preview testing (which also reveals `DebugSimulationBanner` in debug builds).

---

## 5. Sample Payloads & Bundled Assets

- **Open Data Bundled Fallback**: `app/src/main/assets/samples/fountains_sample.json` (real Madrid drinking fountains around Puerta del Sol, Retiro, UPM Campus Sur, Plaza Mayor, and Cibeles).
- **MQTT Test JSON Payloads**: `app/src/test/resources/mqtt/`
  - `report_valid.json` — Valid report with ambient light observation (`ambientLux = 3200.0`, `MODERATE`, `VALID`).
  - `report_null_light.json` — Valid report submitted without light observation (`lightObservation = null`).
  - `report_occluded.json` — Report payload with occluded light observation (`quality = "OCCLUDED"`, `ambientLux = null`, `lightBand = null`).
  - `report_expired.json` — Historical report past the 20-minute TTL (`expiresAt` in the past).

### Report wire contract and live repositories

Reports are UTF-8 JSON with `fountainId`, `status`, optional `lightObservation`, `observedAt`, and `expiresAt` (ISO-8601 UTC timestamps). The optional observation contains `ambientLux`, `lightBand`, `quality`, and its own `observedAt`. A missing or `null` observation is a valid fountain status report. The canonical null-light example is `app/src/test/resources/mqtt/report_null_light.json`.

- Publish topic: `sensoasis/fountains/{fountainId}/reports` (QoS 1, retained).
- Community subscription: `sensoasis/fountains/+/reports`.
- The MQTT connection always uses the injected `MqttConfig` host, port, transport, client ID, and topic root, so the teacher VM can be selected through `MQTT_HOST` / `MQTT_PORT` in `local.properties`. No public broker address is hardcoded in `RealMqttRepository`.
- Madrid fountain JSON is fetched from the official `300051-0-fuentes` download without credentials. Its uppercase dataset fields, WGS84 coordinates, and ETRS89 UTM fallback are parsed. On timeout, HTTP/network error, empty response, or invalid remote JSON, `RealOpenDataRepository` parses the bundled `assets/samples/fountains_sample.json` automatically.
- Hourly daytime outdoor temperature (`08h–20h` `Europe/Madrid`, `temperature_2m`) is fetched by `HourlyWeatherDataSource` in `data.opendata` from the Open-Meteo Forecast API without an API key, persisted in local `SharedPreferences` (`Cached · YYYY-MM-DD`), exposed via `SensOasisViewModel`, and rendered as a smooth cubic Vico `2.1.2` curve on `FountainDetailScreen`.
- Community reports are cached locally per fountain, restored on app start, and filtered against `expiresAt`; MQTT 5 message expiry removes stale retained reports from a compatible broker.

---

## 6. Module Verification Checklist

| Module | Verification Criteria |
|---|---|
| **Ambient Light (`sensor.light`)** | `LightSensorControllerTest` covers median calculation, single-reading window, zero-reading timeout (`UNAVAILABLE`), occlusion short-circuit, and 3-band classification (`LOW`, `MODERATE`, `BRIGHT`). On physical moto g35 5G, covering the top sensor yields `OCCLUDED` and uncovered capture yields `VALID`. |
| **Proximity & Speech (`sensor.proximity`, `speech`)** | `PrivateSpeechAndSummaryTest` covers `NEAR` (2.0s) and `FAR` (0.4s) debounce logic, the `WAITING_FOR_NEAR -> SPEAKING -> WAITING_FOR_NEAR` loop, localized `EN`/`ES`/`CA` summaries, and missing-voice-pack `ERROR` handling. |
| **Open Data & MQTT (`data.opendata`, `data.mqtt`)** | `FountainReportJsonTest`, `MadridFountainsParserTest`, and `PrivateSpeechAndSummaryTest` verify parsing of all 4 MQTT JSON payloads, Madrid municipal fountain JSON, and Open-Meteo hourly forecast JSON. |
| **Compose UI & Navigation (`ui.*`)** | All 7 `SampleData` UI state presets render in `@Preview`. `ReportScreen` requires **zero scrolling** in both portrait and landscape orientations on moto g35 5G, and primary buttons are disabled whenever `interactionLocked == true`. |

---

## 7. Build & Test Commands

```powershell
# Run unit tests
.\gradlew.bat testDebugUnitTest

# Assemble debug APK
.\gradlew.bat assembleDebug
```
