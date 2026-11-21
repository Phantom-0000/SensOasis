package es.upm.sensoasis.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import es.upm.sensoasis.ui.screens.FountainDetailScreen
import es.upm.sensoasis.ui.screens.FountainListScreen
import es.upm.sensoasis.ui.screens.HomeScreen
import es.upm.sensoasis.ui.screens.ReportScreen
import es.upm.sensoasis.viewmodel.SensOasisUiState
import es.upm.sensoasis.viewmodel.UiAction

/**
 * Single-Activity navigation host wiring the four screens:
 * `home` (senior welcome hub) -> `list` -> `detail/{fountainId}` -> `report/{fountainId}`.
 *
 * Lifecycle Rule:
 * `UiAction.LeaveReportScreen` is dispatched only on explicit back/navigation actions, NOT in an
 * unconditional `DisposableEffect.onDispose`, so screen rotation does not interrupt light capture.
 */
@Composable
fun SensOasisNavHost(
    state: SensOasisUiState,
    onAction: (UiAction) -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = SensOasisDestinations.HOME_ROUTE,
        modifier = modifier
    ) {
        composable(route = SensOasisDestinations.HOME_ROUTE) {
            HomeScreen(
                state = state,
                onAction = onAction,
                onOpenNearbyList = {
                    navController.navigate(SensOasisDestinations.LIST_ROUTE)
                },
                onOpenFountainDetail = { fountainId ->
                    onAction(UiAction.SelectFountain(fountainId))
                    navController.navigate(SensOasisDestinations.detailRoute(fountainId))
                }
            )
        }

        composable(route = SensOasisDestinations.LIST_ROUTE) {
            FountainListScreen(
                state = state,
                onAction = onAction,
                onNavigateToDetail = { fountainId ->
                    onAction(UiAction.SelectFountain(fountainId))
                    navController.navigate(SensOasisDestinations.detailRoute(fountainId))
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = SensOasisDestinations.DETAIL_ROUTE,
            arguments = listOf(
                navArgument(SensOasisDestinations.ARG_FOUNTAIN_ID) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val routeFountainId =
                backStackEntry.arguments?.getString(SensOasisDestinations.ARG_FOUNTAIN_ID)
            LaunchedEffect(routeFountainId) {
                if (routeFountainId != null && state.selectedFountain?.id != routeFountainId) {
                    onAction(UiAction.SelectFountain(routeFountainId))
                }
            }
            FountainDetailScreen(
                state = state,
                onAction = onAction,
                onNavigateToReport = { fountainId ->
                    onAction(UiAction.OpenReport)
                    navController.navigate(SensOasisDestinations.reportRoute(fountainId))
                },
                onNavigateBack = {
                    onAction(UiAction.EnablePrivateSpeech(false))
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = SensOasisDestinations.REPORT_ROUTE,
            arguments = listOf(
                navArgument(SensOasisDestinations.ARG_FOUNTAIN_ID) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val routeFountainId =
                backStackEntry.arguments?.getString(SensOasisDestinations.ARG_FOUNTAIN_ID)
            LaunchedEffect(routeFountainId) {
                if (routeFountainId != null && state.selectedFountain?.id != routeFountainId) {
                    onAction(UiAction.SelectFountain(routeFountainId))
                }
                // Back stack restored after process death: ensure proximity sensor restarts.
                if (!state.reportScreenActive) {
                    onAction(UiAction.OpenReport)
                }
            }
            ReportScreen(
                state = state,
                onAction = onAction,
                onNavigateBack = {
                    onAction(UiAction.LeaveReportScreen)
                    navController.popBackStack()
                }
            )
        }
    }
}
