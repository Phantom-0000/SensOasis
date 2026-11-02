import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val mqttHost: String = localProps.getProperty("MQTT_HOST")
    ?: localProps.getProperty("mqtt.host", "test.mosquitto.org")
val mqttPort: Int = (localProps.getProperty("MQTT_PORT") ?: localProps.getProperty("mqtt.port", "1883"))
    .toIntOrNull() ?: 1883
val mqttWsPort: Int = (localProps.getProperty("MQTT_WS_PORT") ?: localProps.getProperty("mqtt.ws.port", "8080"))
    .toIntOrNull() ?: 8080
val mqttTransport: String = localProps.getProperty("MQTT_TRANSPORT")
    ?: localProps.getProperty("mqtt.transport", "TCP")
val mqttTopicRoot: String = localProps.getProperty("MQTT_TOPIC_ROOT")
    ?: localProps.getProperty("mqtt.topic.root", "sensoasis")

android {
    namespace = "es.upm.sensoasis"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "es.upm.sensoasis"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MQTT_HOST", "\"$mqttHost\"")
        buildConfigField("int", "MQTT_PORT", mqttPort.toString())
        buildConfigField("int", "MQTT_WS_PORT", mqttWsPort.toString())
        buildConfigField("String", "MQTT_TRANSPORT", "\"$mqttTransport\"")
        buildConfigField("String", "MQTT_TOPIC_ROOT", "\"$mqttTopicRoot\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    // Serialization & Open Data HTTP
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)

    // MQTTastic & Ktor transport
    implementation(platform(libs.mqtt.client.bom))
    implementation(libs.mqtt.client.core)
    implementation(libs.mqtt.client.transport.tcp)
    implementation(libs.mqtt.client.transport.ws)
    implementation(libs.ktor.client.core)

    // Fused Location Services
    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
