package es.upm.sensoasis

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import es.upm.sensoasis.domain.model.Coordinates
import es.upm.sensoasis.ui.components.DebugSimulationBanner
import es.upm.sensoasis.ui.navigation.SensOasisNavHost
import es.upm.sensoasis.ui.theme.SensOasisTheme
import es.upm.sensoasis.viewmodel.SensOasisViewModel
import es.upm.sensoasis.viewmodel.UiAction
import es.upm.sensoasis.viewmodel.UiEffect

/**
 * Single Activity entry point hosting [SensOasisNavHost] and collecting one-time [UiEffect]s.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as SensOasisApp).container

        setContent {
            SensOasisTheme {
                val viewModel: SensOasisViewModel = viewModel(
                    factory = SensOasisViewModel.factory(appContainer)
                )
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    viewModel.onAction(UiAction.RefreshLocation)
                }

                var hasRequestedLocationPermission by rememberSaveable { mutableStateOf(false) }

                // Request runtime location permissions once on initial startup if real location provider is active
                LaunchedEffect(Unit) {
                    if (!hasRequestedLocationPermission) {
                        hasRequestedLocationPermission = true
                        if (!appContainer.useFakeLocation && !appContainer.fusedLocationProvider.hasLocationPermission()) {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
                }

                // Stop proximity and speech when the entire app moves to background,
                // without interrupting across screen rotations
                DisposableEffect(viewModel) {
                    val lifecycle = ProcessLifecycleOwner.get().lifecycle
                    val observer = object : DefaultLifecycleObserver {
                        override fun onStart(owner: LifecycleOwner) {
                            viewModel.onAppForegrounded()
                        }

                        override fun onStop(owner: LifecycleOwner) {
                            viewModel.onAppBackgrounded()
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose {
                        lifecycle.removeObserver(observer)
                    }
                }

                // Collect one-time UI effects
                LaunchedEffect(viewModel) {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            is UiEffect.ShowMessage -> {
                                snackbarHostState.showSnackbar(effect.message)
                            }
                            is UiEffect.OpenWalkingRoute -> {
                                launchWalkingNavigation(
                                    destination = effect.destination,
                                    label = effect.fountainName
                                ) { errorMessage ->
                                    snackbarHostState.showSnackbar(errorMessage)
                                }
                            }
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (BuildConfig.DEBUG) {
                            DebugSimulationBanner(
                                container = appContainer,
                                currentProximity = uiState.proximityState
                            )
                        }
                        SensOasisNavHost(
                            state = uiState,
                            onAction = viewModel::onAction,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    /**
     * Launches an external map application for walking navigation to the selected fountain.
     */
    private suspend fun launchWalkingNavigation(
        destination: Coordinates,
        label: String,
        onError: suspend (String) -> Unit
    ) {
        val uri = Uri.parse(
            "geo:${destination.latitude},${destination.longitude}?q=${destination.latitude},${destination.longitude}(${Uri.encode(label)})"
        )
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            onError("No map application installed for walking navigation.")
        }
    }
}
