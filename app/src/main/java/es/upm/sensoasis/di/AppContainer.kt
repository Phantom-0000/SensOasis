package es.upm.sensoasis.di

import android.content.Context
import es.upm.sensoasis.BuildConfig
import es.upm.sensoasis.domain.contract.DistanceCalculator
import es.upm.sensoasis.domain.contract.LightSensorController
import es.upm.sensoasis.domain.contract.LocationProvider
import es.upm.sensoasis.domain.contract.MqttRepository
import es.upm.sensoasis.domain.contract.OpenDataRepository
import es.upm.sensoasis.domain.contract.PrivateSpeechController
import es.upm.sensoasis.domain.contract.ProximityStateProvider
import es.upm.sensoasis.domain.contract.ReportUseCase
import es.upm.sensoasis.domain.contract.SpeechController
import es.upm.sensoasis.domain.contract.SpeechSummaryUseCase
import es.upm.sensoasis.domain.model.MqttConfig
import es.upm.sensoasis.domain.model.MqttTransport
import es.upm.sensoasis.data.mqtt.RealMqttRepository
import es.upm.sensoasis.data.opendata.RealOpenDataRepository
import es.upm.sensoasis.fake.FakeLightSensorController
import es.upm.sensoasis.fake.FakeLocationProvider
import es.upm.sensoasis.fake.FakeMqttRepository
import es.upm.sensoasis.fake.FakeOpenDataRepository
import es.upm.sensoasis.fake.FakePrivateSpeechController
import es.upm.sensoasis.fake.FakeProximityStateProvider
import es.upm.sensoasis.fake.FakeReportUseCase
import es.upm.sensoasis.fake.FakeSpeechController
import es.upm.sensoasis.fake.FakeSpeechSummaryUseCase
import es.upm.sensoasis.location.FusedLocationProvider
import es.upm.sensoasis.location.HaversineDistanceCalculator
import es.upm.sensoasis.sensor.light.RealLightSensorController
import es.upm.sensoasis.usecase.SubmitFountainReportUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.UUID

/**
 * Manual Dependency Injection container.
 *
 * Each module has an independent `useFake*` switch so real implementations can be
 * enabled one at a time without blocking parallel development.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    // Per-module switches. Edit these values in code and rebuild to switch a module
    // from Fake to Real (dependencies are created lazily once).
    val useFakeOpenData: Boolean = false
    val useFakeMqtt: Boolean = false
    val useFakeLightSensor: Boolean = false
    val useFakeProximity: Boolean = true
    val useFakeSpeech: Boolean = true
    val useFakeLocation: Boolean = false

    /** Application-wide scope for work that must outlive a ViewModel (e.g. MQTT disconnect). */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Unique per-installation MQTT clientId so two phones never collide
     * on the Mosquitto broker during multi-device testing.
     */
    val mqttConfig: MqttConfig by lazy {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedId = prefs.getString(KEY_MQTT_CLIENT_ID, null) ?: run {
            val generated = "sensoasis-${UUID.randomUUID().toString().take(8)}"
            prefs.edit().putString(KEY_MQTT_CLIENT_ID, generated).apply()
            generated
        }
        val transport = if (BuildConfig.MQTT_TRANSPORT.equals("WS", ignoreCase = true)) {
            MqttTransport.WS
        } else {
            MqttTransport.TCP
        }
        MqttConfig(
            host = BuildConfig.MQTT_HOST,
            port = BuildConfig.MQTT_PORT,
            wsPort = BuildConfig.MQTT_WS_PORT,
            transport = transport,
            topicRoot = BuildConfig.MQTT_TOPIC_ROOT,
            clientId = storedId
        )
    }

    val distanceCalculator: DistanceCalculator by lazy {
        HaversineDistanceCalculator()
    }

    // Fake instances exposed for debug simulation banner & tests
    val fakeProximityStateProvider: FakeProximityStateProvider by lazy {
        FakeProximityStateProvider()
    }

    val proximityStateProvider: ProximityStateProvider by lazy {
        if (useFakeProximity) {
            fakeProximityStateProvider
        } else {
            fakeProximityStateProvider
        }
    }

    val fakeLightSensorController: FakeLightSensorController by lazy {
        FakeLightSensorController(proximityStateProvider = proximityStateProvider)
    }

    val realLightSensorController: RealLightSensorController by lazy {
        RealLightSensorController(
            context = appContext,
            proximityStateProvider = proximityStateProvider
        )
    }

    val lightSensorController: LightSensorController by lazy {
        if (useFakeLightSensor) {
            fakeLightSensorController
        } else {
            realLightSensorController
        }
    }

    val fakeOpenDataRepository: FakeOpenDataRepository by lazy {
        FakeOpenDataRepository()
    }

    val openDataRepository: OpenDataRepository by lazy {
        if (useFakeOpenData) {
            fakeOpenDataRepository
        } else {
            RealOpenDataRepository(context = appContext)
        }
    }

    val fakeMqttRepository: FakeMqttRepository by lazy {
        FakeMqttRepository()
    }

    val mqttRepository: MqttRepository by lazy {
        if (useFakeMqtt) {
            fakeMqttRepository
        } else {
            RealMqttRepository(context = appContext, config = mqttConfig, scope = applicationScope)
        }
    }

    val reportUseCase: ReportUseCase by lazy {
        SubmitFountainReportUseCase(
            lightSensorController = lightSensorController,
            mqttRepository = mqttRepository
        )
    }

    val speechSummaryUseCase: SpeechSummaryUseCase by lazy {
        if (useFakeSpeech) {
            FakeSpeechSummaryUseCase()
        } else {
            FakeSpeechSummaryUseCase()
        }
    }

    val speechController: SpeechController by lazy {
        if (useFakeSpeech) {
            FakeSpeechController()
        } else {
            FakeSpeechController()
        }
    }

    val privateSpeechController: PrivateSpeechController by lazy {
        if (useFakeSpeech) {
            FakePrivateSpeechController(
                proximityStateProvider = proximityStateProvider,
                summaryUseCase = speechSummaryUseCase
            )
        } else {
            FakePrivateSpeechController(
                proximityStateProvider = proximityStateProvider,
                summaryUseCase = speechSummaryUseCase
            )
        }
    }

    val fakeLocationProvider: FakeLocationProvider by lazy {
        FakeLocationProvider()
    }

    val fusedLocationProvider: FusedLocationProvider by lazy {
        FusedLocationProvider(context = appContext)
    }

    val locationProvider: LocationProvider by lazy {
        if (useFakeLocation) {
            fakeLocationProvider
        } else {
            fusedLocationProvider
        }
    }

    companion object {
        private const val PREFS_NAME = "sensoasis_prefs"
        private const val KEY_MQTT_CLIENT_ID = "mqtt_client_id"
    }
}
