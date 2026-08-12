package com.revanced.net.revancedmanager.presentation.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.revanced.net.revancedmanager.presentation.bloc.AppBloc
import com.revanced.net.revancedmanager.presentation.bloc.AppEvent
import com.revanced.net.revancedmanager.presentation.bloc.AppState
import com.revanced.net.revancedmanager.presentation.bloc.clearApkCache
import com.revanced.net.revancedmanager.presentation.bloc.loadConfigSafely
import com.revanced.net.revancedmanager.presentation.bloc.getApkCacheInfo
import com.revanced.net.revancedmanager.presentation.bloc.shareDebugLogs
import com.revanced.net.revancedmanager.presentation.ui.screen.AppDetailScreen
import com.revanced.net.revancedmanager.presentation.ui.screen.MainScreen
import com.revanced.net.revancedmanager.presentation.ui.screen.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

/** The app list — the start destination. */
@Serializable
object AppListRoute

/**
 * One app's detail page, addressed by catalog entry rather than by package: several entries can
 * share a package, and the page has to show the one that was tapped.
 */
@Serializable
data class AppDetailRoute(val appId: String)

@Serializable
object SettingsRoute

/**
 * The app's navigation graph.
 *
 * Settings used to be a boolean on the success state, which meant back had to be routed through
 * the bloc and a third screen would have needed a third flag. Destinations are real destinations
 * now, so the system back button and the back stack do their own jobs.
 *
 * All three destinations share one [AppBloc], obtained here at the graph level. The detail screen
 * needs the live download progress and install status the bloc streams into the app list; a second
 * view model would have to duplicate the whole download and install pipeline to see them.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val bloc: AppBloc = hiltViewModel()

    // Toasts are raised from favouriting, installing and uninstalling, all of which can now be
    // started from the detail screen — so they are shown here rather than on the list, which is
    // not composed while another destination is on top.
    AppToastHost(bloc)

    NavHost(navController = navController, startDestination = AppListRoute) {
        composable<AppListRoute> {
            MainScreen(
                viewModel = bloc,
                onOpenDetail = { appId -> navController.navigate(AppDetailRoute(appId)) },
                onOpenSettings = { navController.navigate(SettingsRoute) }
            )
        }

        composable<AppDetailRoute> { backStackEntry ->
            AppDetailScreen(
                appId = backStackEntry.toRoute<AppDetailRoute>().appId,
                viewModel = bloc,
                onBack = { navController.popBackStack() }
            )
        }

        composable<SettingsRoute> {
            val state by bloc.state.collectAsState()

            // Loading carries no config, and the settings button is on the bar from the first
            // frame — so opening settings before the list finishes must not show defaults. Saving
            // those would overwrite the user's real settings with the factory ones.
            val storedConfig = remember { bloc.loadConfigSafely() }
            val config = when (val s = state) {
                is AppState.Success -> s.config
                is AppState.Error -> s.config
                is AppState.Loading -> storedConfig
            }

            // Settings used to be handed its config by the navigate-to-settings event. Now the
            // destination asks for it on entry instead.
            LaunchedEffect(Unit) { bloc.handleEvent(AppEvent.LoadConfiguration) }

            SettingsScreen(
                currentConfig = config,
                onSave = { newConfig ->
                    bloc.handleEvent(AppEvent.SaveSettings(newConfig))
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
                onResetSettings = {
                    bloc.handleEvent(AppEvent.ResetSettings)
                    navController.popBackStack()
                },
                getLogContent = { bloc.debugLogManager.getLastLines() },
                onShareLogs = { bloc.shareDebugLogs() },
                getApkCacheInfo = { bloc.getApkCacheInfo() },
                onClearApkCache = { bloc.clearApkCache() }
            )
        }
    }
}

/**
 * Shows the bloc's toast messages, cancelling the previous one so a burst of updates does not
 * queue up behind itself.
 */
@Composable
private fun AppToastHost(bloc: AppBloc) {
    val context = LocalContext.current
    val toastMessage by bloc.toastMessage.collectAsState()
    var currentToast by remember { mutableStateOf<Toast?>(null) }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { message ->
            currentToast?.cancel()

            val newToast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
            currentToast = newToast
            newToast.show()

            bloc.clearToast()

            delay(1000)
            newToast.cancel()
            currentToast = null
        }
    }
}
