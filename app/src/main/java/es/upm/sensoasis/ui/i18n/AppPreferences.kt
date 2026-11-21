package es.upm.sensoasis.ui.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import es.upm.sensoasis.domain.model.AppLanguage
import es.upm.sensoasis.domain.model.FountainStatus
import es.upm.sensoasis.domain.model.LightBand
import es.upm.sensoasis.domain.model.LightQuality
import java.time.Duration
import java.time.Instant

/**
 * Senior-friendly text scale presets switchable with one tap on HomeScreen.
 */
enum class AppFontScale(val key: String, val shortLabel: String, val multiplier: Float) {
    STANDARD("std", "A", 1.0f),
    EXTRA_LARGE("xl", "A+", 1.18f);

    companion object {
        fun fromKey(key: String?): AppFontScale =
            entries.firstOrNull { it.key == key } ?: STANDARD
    }
}

/**
 * Persistent UI preferences holder for language and font size.
 */
@Stable
class AppPreferencesState(
    initialLanguage: AppLanguage = AppLanguage.EN,
    initialFontScale: AppFontScale = AppFontScale.STANDARD,
    private val onPersist: (AppLanguage, AppFontScale) -> Unit = { _, _ -> }
) {
    var language: AppLanguage by mutableStateOf(initialLanguage)
        private set

    var fontScale: AppFontScale by mutableStateOf(initialFontScale)
        private set

    fun updateLanguage(newLanguage: AppLanguage) {
        if (language != newLanguage) {
            language = newLanguage
            onPersist(newLanguage, fontScale)
        }
    }

    fun updateFontScale(newScale: AppFontScale) {
        if (fontScale != newScale) {
            fontScale = newScale
            onPersist(language, newScale)
        }
    }
}

@Composable
fun rememberAppPreferencesState(): AppPreferencesState {
    val context = LocalContext.current
    val prefs = remember(context) {
        runCatching {
            context.applicationContext.getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
    }
    return remember(prefs) {
        val savedLang = AppLanguage.fromCode(prefs?.getString(KEY_LANGUAGE, null))
        val savedScale = AppFontScale.fromKey(prefs?.getString(KEY_FONT_SCALE, null))
        AppPreferencesState(
            initialLanguage = savedLang,
            initialFontScale = savedScale,
            onPersist = { lang, scale ->
                prefs?.edit()
                    ?.putString(KEY_LANGUAGE, lang.code)
                    ?.putString(KEY_FONT_SCALE, scale.key)
                    ?.apply()
            }
        )
    }
}

private const val UI_PREFS_NAME = "sensoasis_ui_preferences"
private const val KEY_LANGUAGE = "ui_language"
private const val KEY_FONT_SCALE = "ui_font_scale"

val LocalAppPreferences = staticCompositionLocalOf { AppPreferencesState() }
val LocalAppStrings = staticCompositionLocalOf { stringsFor(AppLanguage.EN) }

/**
 * Complete localized UI strings across all four screens.
 */
data class AppStrings(
    val appSubtitle: String,
    val languageLabel: String,
    val textSizeLabel: String,
    val liveOnline: String,
    val liveConnecting: String,
    val liveOffline: String,
    val gpsActive: String,
    val madridCenter: String,
    val refresh: String,
    val home: String,
    val back: String,
    val cancel: String,
    val nearestWorkingTitle: String,
    val nearestFallbackTitle: String,
    val walkThere: String,
    val detailsAndVoice: String,
    val browseNearbyFountains: (Int) -> String,
    val browseNearbySimple: String,
    val nearbyListHeader: String,
    val loadingFountains: String,
    val noFountainsAvailable: String,
    val distanceAway: (String) -> String,
    val distanceLabel: (String) -> String,
    val distanceUnavailable: String,
    val statusWorking: String,
    val statusWorkingDesc: String,
    val statusNotWorking: String,
    val statusNotWorkingDesc: String,
    val statusUnknown: String,
    val statusUnknownDesc: String,
    val workingOnlyTitle: String,
    val showingWorkingCount: (Int, Int) -> String,
    val showingAllCount: (Int) -> String,
    val noWorkingMatchFilter: String,
    val showAllFountains: String,
    val communityReportTitle: String,
    val latestCommunityReportTitle: String,
    val noRecentReport: String,
    val officialStatusPrefix: String,
    val reportedStatusPrefix: String,
    val fountainDetailsTitle: String,
    val coordinatesPrefix: String,
    val sunlightObservationTitle: String,
    val sunlightNotIncluded: String,
    val lightBrightTitle: String,
    val lightBrightDesc: String,
    val lightModerateTitle: String,
    val lightModerateDesc: String,
    val lightLowTitle: String,
    val lightLowDesc: String,
    val qualityValid: String,
    val qualityOccluded: String,
    val qualityUnavailable: String,
    val chartTitle: String,
    val chartSubtitle: String,
    val chartBadgeLive: (String) -> String,
    val chartBadgeCached: (String) -> String,
    val chartPeakSummary: (Int, Int, Int) -> String,
    val chartHeatAlert: (Int, Int) -> String,
    val chartNoCacheYet: String,
    val listenPrivatelyTitle: String,
    val listenPrivatelyDesc: String,
    val speechOff: String,
    val speechWaiting: String,
    val speechSpeaking: String,
    val speechError: String,
    val earSensorLabel: String,
    val earNear: String,
    val earFar: String,
    val earReady: String,
    val buttonsLockedBanner: String,
    val stopSpeaking: String,
    val unlockHint: String,
    val walkingRoute: String,
    val reportStatus: String,
    val reportFountainTitle: String,
    val step1WaterWorking: String,
    val step2SunlightCheck: String,
    val step2SunlightCompact: String,
    val sensorClear: String,
    val sensorCovered: String,
    val sensorReady: String,
    val statusPrefix: String,
    val stateReadyToSend: String,
    val stateMeasuring: String,
    val stateTopCovered: String,
    val stateLightUnavailable: String,
    val stateSending: String,
    val stateSentSuccess: String,
    val stateOffline: String,
    val stateError: String,
    val lightPromptIdle: String,
    val lightMeasuredPrefix: String,
    val lightOccludedMsg: String,
    val lightUnavailableMsg: (String) -> String,
    val measureSunlightBtn: String,
    val measuringBrightnessBtn: String,
    val confirmSendBtn: String,
    val sendingReportBtn: String,
    val sentSuccessBtn: String,
    val offlineRetryBtn: String,
    val retrySendBtn: String,
    val ageJustNow: String,
    val ageOneMin: String,
    val ageMinutes: (Long) -> String,
    val ageHours: (Long) -> String
) {
    fun formatStatusName(status: FountainStatus): String = when (status) {
        FountainStatus.WORKING -> statusWorking
        FountainStatus.NOT_WORKING -> statusNotWorking
        FountainStatus.UNKNOWN -> statusUnknown
    }

    fun formatBandTitle(band: LightBand?): String = when (band) {
        LightBand.BRIGHT -> lightBrightTitle
        LightBand.MODERATE -> lightModerateTitle
        LightBand.LOW -> lightLowTitle
        null -> qualityUnavailable
    }

    fun formatBandDesc(band: LightBand?): String = when (band) {
        LightBand.BRIGHT -> lightBrightDesc
        LightBand.MODERATE -> lightModerateDesc
        LightBand.LOW -> lightLowDesc
        null -> qualityUnavailable
    }

    fun formatQuality(quality: LightQuality): String = when (quality) {
        LightQuality.VALID -> qualityValid
        LightQuality.OCCLUDED -> qualityOccluded
        LightQuality.UNAVAILABLE -> qualityUnavailable
    }

    fun formatSensorReason(reason: String, lang: AppLanguage): String = when (reason) {
        "TYPE_LIGHT_MISSING" -> when (lang) {
            AppLanguage.EN -> "Light sensor not supported on this device"
            AppLanguage.ES -> "Sensor de luz no soportado en este dispositivo"
            AppLanguage.CA -> "Sensor de llum no compatible en aquest dispositiu"
        }
        "PROXIMITY_UNCONFIRMED" -> when (lang) {
            AppLanguage.EN -> "Proximity check unconfirmed"
            AppLanguage.ES -> "Sensor de proximidad sin confirmar"
            AppLanguage.CA -> "Sensor de proximitat sense confirmar"
        }
        "SENSOR_REGISTRATION_FAILED" -> when (lang) {
            AppLanguage.EN -> "Failed to start light sensor"
            AppLanguage.ES -> "No se pudo iniciar el sensor de luz"
            AppLanguage.CA -> "No s'ha pogut iniciar el sensor de llum"
        }
        "LIGHT_TIMEOUT" -> when (lang) {
            AppLanguage.EN -> "Measurement timed out"
            AppLanguage.ES -> "Tiempo de medición agotado"
            AppLanguage.CA -> "Temps de mesura esgotat"
        }
        else -> reason.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }

    fun formatAge(observedAt: Instant, now: Instant = Instant.now()): String {
        val minutes = Duration.between(observedAt, now).toMinutes().coerceAtLeast(0L)
        return when {
            minutes < 1 -> ageJustNow
            minutes == 1L -> ageOneMin
            minutes < 60 -> ageMinutes(minutes)
            else -> ageHours((minutes / 60).coerceAtLeast(1L))
        }
    }
}

fun stringsFor(language: AppLanguage): AppStrings = when (language) {
    AppLanguage.EN -> EnglishStrings
    AppLanguage.ES -> SpanishStrings
    AppLanguage.CA -> CatalanStrings
}

private val EnglishStrings = AppStrings(
    appSubtitle = "Drinking water & comfort",
    languageLabel = "Language",
    textSizeLabel = "Text Size",
    liveOnline = "Live Updates: Online",
    liveConnecting = "Live Updates: Connecting",
    liveOffline = "Live Updates: Offline",
    gpsActive = "GPS Active",
    madridCenter = "Madrid Center",
    refresh = "Refresh",
    home = "Home",
    back = "Back",
    cancel = "Cancel",
    nearestWorkingTitle = "Nearest Working Fountain",
    nearestFallbackTitle = "Closest Fountain",
    walkThere = "Walk There",
    detailsAndVoice = "Details & Voice",
    browseNearbyFountains = { count -> "Show Nearby Fountains ($count)" },
    browseNearbySimple = "Show Nearby Fountains",
    nearbyListHeader = "Nearby",
    loadingFountains = "Loading nearby fountains...",
    noFountainsAvailable = "No fountains available right now. Tap Refresh to retry.",
    distanceAway = { d -> "$d away" },
    distanceLabel = { d -> "Distance: $d" },
    distanceUnavailable = "Distance unavailable",
    statusWorking = "Working",
    statusWorkingDesc = "Clean drinking water available",
    statusNotWorking = "Not Working",
    statusNotWorkingDesc = "Out of service",
    statusUnknown = "Status Unknown",
    statusUnknownDesc = "Check on arrival",
    workingOnlyTitle = "Working fountains only",
    showingWorkingCount = { w, total -> "Showing $w working of $total" },
    showingAllCount = { total -> "Showing all $total fountains" },
    noWorkingMatchFilter = "No working fountains match this filter.",
    showAllFountains = "Show All Fountains",
    communityReportTitle = "Community Report",
    latestCommunityReportTitle = "Latest Community Report",
    noRecentReport = "No recent community report (expires after 20 min).",
    officialStatusPrefix = "Official",
    reportedStatusPrefix = "Reported",
    fountainDetailsTitle = "Fountain Details",
    coordinatesPrefix = "Coordinates",
    sunlightObservationTitle = "Ambient Light Check",
    sunlightNotIncluded = "Ambient light: Not included in this report.",
    lightBrightTitle = "Bright Sunlight",
    lightBrightDesc = "High glare / exposed area",
    lightModerateTitle = "Moderate Light",
    lightModerateDesc = "Comfortable visibility (low glare)",
    lightLowTitle = "Low Light",
    lightLowDesc = "Shaded or dim area",
    qualityValid = "Verified reading",
    qualityOccluded = "Sensor covered",
    qualityUnavailable = "Unavailable",
    chartTitle = "Today's Temperature Curve (°C)",
    chartSubtitle = "Hourly outdoor forecast (08h–20h) to plan comfortable hydration walks",
    chartBadgeLive = { date -> "Live · $date" },
    chartBadgeCached = { date -> "Cached · $date" },
    chartPeakSummary = { maxT, peakH, minT -> "Peak: ${maxT}°C around ${peakH}:00 (8h: ${minT}°C) — Stay hydrated." },
    chartHeatAlert = { maxT, peakH -> "Heat alert: ${maxT}°C around ${peakH}:00 — Avoid midday sun & drink water!" },
    chartNoCacheYet = "Connect to the internet once to load and save today's temperature curve.",
    listenPrivatelyTitle = "Listen Privately (Hold to Ear)",
    listenPrivatelyDesc = "Lift phone to your ear like a call for 2s. Use side volume keys while at ear to adjust loudness.",
    speechOff = "Off — turn on switch above to listen",
    speechWaiting = "Ready — lift phone to your ear like a call",
    speechSpeaking = "Speaking privately through top earpiece",
    speechError = "English voice pack is missing or unavailable on this device (check Android TTS settings).",
    earSensorLabel = "Ear sensor",
    earNear = "Near ear",
    earFar = "Away from ear",
    earReady = "Ready",
    buttonsLockedBanner = "Buttons locked while phone is at your ear.",
    stopSpeaking = "Stop Speaking",
    unlockHint = "Move phone away from ear or tap Stop Speaking to unlock.",
    walkingRoute = "Walking Route",
    reportStatus = "Report Status",
    reportFountainTitle = "Report Fountain",
    step1WaterWorking = "1. Is Water Working?",
    step2SunlightCheck = "2. Ambient Light Check",
    step2SunlightCompact = "2. Ambient Light (Optional)",
    sensorClear = "Uncovered",
    sensorCovered = "Covered",
    sensorReady = "Ready",
    statusPrefix = "Status",
    stateReadyToSend = "Ready to send",
    stateMeasuring = "Measuring light",
    stateTopCovered = "Top covered",
    stateLightUnavailable = "Light unavailable",
    stateSending = "Sending...",
    stateSentSuccess = "Sent!",
    stateOffline = "Offline",
    stateError = "Error",
    lightPromptIdle = "Hold phone face-up uncovered to measure light & glare, or send status directly.",
    lightMeasuredPrefix = "Measured",
    lightOccludedMsg = "Top of phone was covered — report will be sent without light data.",
    lightUnavailableMsg = { r -> "Light check unavailable ($r). You can still send." },
    measureSunlightBtn = "Measure Ambient Light (1.5s)",
    measuringBrightnessBtn = "Measuring brightness...",
    confirmSendBtn = "Confirm & Send Fountain Report",
    sendingReportBtn = "Sending Fountain Report...",
    sentSuccessBtn = "Report Sent Successfully!",
    offlineRetryBtn = "Offline — Tap to Retry",
    retrySendBtn = "Retry Sending Report",
    ageJustNow = "Just now",
    ageOneMin = "1 min ago",
    ageMinutes = { m -> "$m min ago" },
    ageHours = { h -> "${h}h ago" }
)

private val SpanishStrings = AppStrings(
    appSubtitle = "Agua potable y luz cómoda",
    languageLabel = "Idioma",
    textSizeLabel = "Tamaño letra",
    liveOnline = "En vivo: Conectado",
    liveConnecting = "En vivo: Conectando",
    liveOffline = "En vivo: Sin conexión",
    gpsActive = "GPS activo",
    madridCenter = "Madrid Centro",
    refresh = "Actualizar",
    home = "Inicio",
    back = "Volver",
    cancel = "Cancelar",
    nearestWorkingTitle = "Fuente operativa más cercana",
    nearestFallbackTitle = "Fuente más cercana",
    walkThere = "Ir caminando",
    detailsAndVoice = "Detalles y voz",
    browseNearbyFountains = { count -> "Ver fuentes cercanas ($count)" },
    browseNearbySimple = "Ver fuentes cercanas",
    nearbyListHeader = "Cercanas",
    loadingFountains = "Cargando fuentes cercanas...",
    noFountainsAvailable = "No hay fuentes disponibles. Pulse Actualizar.",
    distanceAway = { d -> "A $d" },
    distanceLabel = { d -> "Distancia: $d" },
    distanceUnavailable = "Distancia no disponible",
    statusWorking = "Operativa",
    statusWorkingDesc = "Agua potable disponible",
    statusNotWorking = "No operativa",
    statusNotWorkingDesc = "Fuera de servicio",
    statusUnknown = "Estado desconocido",
    statusUnknownDesc = "Comprobar al llegar",
    workingOnlyTitle = "Solo fuentes operativas",
    showingWorkingCount = { w, total -> "Mostrando $w operativas de $total" },
    showingAllCount = { total -> "Mostrando las $total fuentes" },
    noWorkingMatchFilter = "Ninguna fuente operativa con este filtro.",
    showAllFountains = "Mostrar todas las fuentes",
    communityReportTitle = "Aviso ciudadano",
    latestCommunityReportTitle = "Último aviso ciudadano",
    noRecentReport = "Sin avisos recientes (caducan a los 20 min).",
    officialStatusPrefix = "Oficial",
    reportedStatusPrefix = "Aviso",
    fountainDetailsTitle = "Detalle de la fuente",
    coordinatesPrefix = "Coordenadas",
    sunlightObservationTitle = "Luz ambiental en la fuente",
    sunlightNotIncluded = "Luz ambiental: No incluida en este aviso.",
    lightBrightTitle = "Luz brillante",
    lightBrightDesc = "Alto deslumbramiento / zona expuesta",
    lightModerateTitle = "Luz moderada",
    lightModerateDesc = "Visibilidad cómoda (poco reflejo)",
    lightLowTitle = "Poca luz",
    lightLowDesc = "Zona sombreada o tenue",
    qualityValid = "Lectura válida",
    qualityOccluded = "Sensor tapado",
    qualityUnavailable = "No disponible",
    chartTitle = "Curva de temperatura hoy (°C)",
    chartSubtitle = "Previsión exterior por horas (08h–20h) para pasear e hidratarse",
    chartBadgeLive = { date -> "En vivo · $date" },
    chartBadgeCached = { date -> "Guardado · $date" },
    chartPeakSummary = { maxT, peakH, minT -> "Máxima: ${maxT}°C hacia las ${peakH}:00 (8h: ${minT}°C) — Hidrátese." },
    chartHeatAlert = { maxT, peakH -> "Aviso de calor: ${maxT}°C a las ${peakH}:00 — Evite el sol central y beba agua." },
    chartNoCacheYet = "Conéctese una vez a internet para descargar y guardar la curva de hoy.",
    listenPrivatelyTitle = "Escuchar en privado (al oído)",
    listenPrivatelyDesc = "Acerque el móvil al oído como una llamada durante 2 s. Use las teclas laterales de volumen para subir el sonido.",
    speechOff = "Apagado — active el interruptor superior",
    speechWaiting = "Listo — acerque el móvil a su oído",
    speechSpeaking = "Hablando en privado por el auricular",
    speechError = "El paquete de voz en español falta o no está disponible en este dispositivo (revise ajustes TTS).",
    earSensorLabel = "Sensor oído",
    earNear = "Cerca del oído",
    earFar = "Lejos del oído",
    earReady = "Listo",
    buttonsLockedBanner = "Botones bloqueados mientras escucha al oído.",
    stopSpeaking = "Detener voz",
    unlockHint = "Retire el móvil del oído o pulse Detener voz.",
    walkingRoute = "Ruta a pie",
    reportStatus = "Informar estado",
    reportFountainTitle = "Informar fuente",
    step1WaterWorking = "1. ¿Sale agua?",
    step2SunlightCheck = "2. Medición de luz ambiental",
    step2SunlightCompact = "2. Luz ambiental (Opcional)",
    sensorClear = "Libre",
    sensorCovered = "Tapado",
    sensorReady = "Listo",
    statusPrefix = "Estado",
    stateReadyToSend = "Listo para enviar",
    stateMeasuring = "Midiendo luz",
    stateTopCovered = "Sensor tapado",
    stateLightUnavailable = "Luz no disponible",
    stateSending = "Enviando...",
    stateSentSuccess = "¡Enviado!",
    stateOffline = "Sin conexión",
    stateError = "Error",
    lightPromptIdle = "Sostenga el móvil boca arriba sin tapar para medir la luz ambiental, o envíe directamente.",
    lightMeasuredPrefix = "Medido",
    lightOccludedMsg = "Parte superior tapada — se enviará sin datos de luz.",
    lightUnavailableMsg = { r -> "Sensor de luz no disponible ($r). Puede enviar igual." },
    measureSunlightBtn = "Medir luz ambiental (1,5 s)",
    measuringBrightnessBtn = "Midiendo brillo...",
    confirmSendBtn = "Confirmar y enviar aviso",
    sendingReportBtn = "Enviando aviso...",
    sentSuccessBtn = "¡Aviso enviado con éxito!",
    offlineRetryBtn = "Sin conexión — Reintentar",
    retrySendBtn = "Reintentar envío",
    ageJustNow = "Ahora mismo",
    ageOneMin = "Hace 1 min",
    ageMinutes = { m -> "Hace $m min" },
    ageHours = { h -> "Hace ${h}h" }
)

private val CatalanStrings = AppStrings(
    appSubtitle = "Aigua potable i llum còmoda",
    languageLabel = "Idioma",
    textSizeLabel = "Mida lletra",
    liveOnline = "En viu: Connectat",
    liveConnecting = "En viu: Connectant",
    liveOffline = "En viu: Sense connexió",
    gpsActive = "GPS actiu",
    madridCenter = "Madrid Centre",
    refresh = "Actualitzar",
    home = "Inici",
    back = "Tornar",
    cancel = "Cancel·lar",
    nearestWorkingTitle = "Font operativa més propera",
    nearestFallbackTitle = "Font més propera",
    walkThere = "Anar-hi caminant",
    detailsAndVoice = "Detalls i veu",
    browseNearbyFountains = { count -> "Veure fonts properes ($count)" },
    browseNearbySimple = "Veure fonts properes",
    nearbyListHeader = "Properes",
    loadingFountains = "Carregant fonts properes...",
    noFountainsAvailable = "Cap font disponible ara mateix. Premeu Actualitzar.",
    distanceAway = { d -> "A $d" },
    distanceLabel = { d -> "Distància: $d" },
    distanceUnavailable = "Distància no disponible",
    statusWorking = "Operativa",
    statusWorkingDesc = "Aigua potable disponible",
    statusNotWorking = "No operativa",
    statusNotWorkingDesc = "Fora de servei",
    statusUnknown = "Estat desconegut",
    statusUnknownDesc = "Comprovar en arribar",
    workingOnlyTitle = "Només fonts operatives",
    showingWorkingCount = { w, total -> "Mostrant $w operatives de $total" },
    showingAllCount = { total -> "Mostrant les $total fonts" },
    noWorkingMatchFilter = "Cap font operativa amb aquest filtre.",
    showAllFountains = "Mostrar totes les fonts",
    communityReportTitle = "Avís ciutadà",
    latestCommunityReportTitle = "Últim avís ciutadà",
    noRecentReport = "Sense avisos recents (caduquen als 20 min).",
    officialStatusPrefix = "Oficial",
    reportedStatusPrefix = "Avís",
    fountainDetailsTitle = "Detall de la font",
    coordinatesPrefix = "Coordenades",
    sunlightObservationTitle = "Llum ambiental a la font",
    sunlightNotIncluded = "Llum ambiental: No inclosa en aquest avís.",
    lightBrightTitle = "Llum brillant",
    lightBrightDesc = "Alt enlluernament / zona exposada",
    lightModerateTitle = "Llum moderada",
    lightModerateDesc = "Visibilitat còmoda (poc reflex)",
    lightLowTitle = "Poca llum",
    lightLowDesc = "Zona ombrejada o tènue",
    qualityValid = "Lectura vàlida",
    qualityOccluded = "Sensor tapat",
    qualityUnavailable = "No disponible",
    chartTitle = "Corba de temperatura avui (°C)",
    chartSubtitle = "Previsió exterior per hores (08h–20h) per passejar i hidratar-se",
    chartBadgeLive = { date -> "En viu · $date" },
    chartBadgeCached = { date -> "Desat · $date" },
    chartPeakSummary = { maxT, peakH, minT -> "Màxima: ${maxT}°C cap a les ${peakH}:00 (8h: ${minT}°C) — Hidrateu-vos." },
    chartHeatAlert = { maxT, peakH -> "Avís de calor: ${maxT}°C a les ${peakH}:00 — Eviteu el sol central i beveu aigua." },
    chartNoCacheYet = "Connecteu-vos un cop a internet per carregar i desar la corba d'avui.",
    listenPrivatelyTitle = "Escoltar en privat (a l'orella)",
    listenPrivatelyDesc = "Acosteu el mòbil a l'orella com una trucada durant 2 s. Useu les tecles laterals de volum per ajustar el so.",
    speechOff = "Apagat — activeu l'interruptor superior",
    speechWaiting = "Llest — acosteu el mòbil a l'orella",
    speechSpeaking = "Parlant en privat per l'auricular",
    speechError = "El paquet de veu en català falta o no està disponible en aquest dispositiu (reviseu ajustos TTS).",
    earSensorLabel = "Sensor orella",
    earNear = "A prop de l'orella",
    earFar = "Lluny de l'orella",
    earReady = "Llest",
    buttonsLockedBanner = "Botons bloquejats mentre escolteu a l'orella.",
    stopSpeaking = "Aturar veu",
    unlockHint = "Retireu el mòbil de l'orella o premeu Aturar veu.",
    walkingRoute = "Ruta a peu",
    reportStatus = "Informar estat",
    reportFountainTitle = "Informar font",
    step1WaterWorking = "1. Surt aigua?",
    step2SunlightCheck = "2. Mesura de llum ambiental",
    step2SunlightCompact = "2. Llum ambiental (Opcional)",
    sensorClear = "Lliure",
    sensorCovered = "Tapat",
    sensorReady = "Llest",
    statusPrefix = "Estat",
    stateReadyToSend = "Llest per enviar",
    stateMeasuring = "Mesurant llum",
    stateTopCovered = "Sensor tapat",
    stateLightUnavailable = "Llum no disponible",
    stateSending = "Enviant...",
    stateSentSuccess = "Enviat!",
    stateOffline = "Sense connexió",
    stateError = "Error",
    lightPromptIdle = "Sostingueu el mòbil cara amunt sense tapar per mesurar la llum ambiental, o envieu directament.",
    lightMeasuredPrefix = "Mesurat",
    lightOccludedMsg = "Part superior tapada — s'enviarà sense dades de llum.",
    lightUnavailableMsg = { r -> "Sensor de llum no disponible ($r). Podeu enviar igualment." },
    measureSunlightBtn = "Mesurar llum ambiental (1,5 s)",
    measuringBrightnessBtn = "Mesurant brillantor...",
    confirmSendBtn = "Confirmar i enviar avís",
    sendingReportBtn = "Enviant avís...",
    sentSuccessBtn = "Avís enviat amb èxit!",
    offlineRetryBtn = "Sense connexió — Reintentar",
    retrySendBtn = "Reintentar enviament",
    ageJustNow = "Ara mateix",
    ageOneMin = "Fa 1 min",
    ageMinutes = { m -> "Fa $m min" },
    ageHours = { h -> "Fa ${h}h" }
)
