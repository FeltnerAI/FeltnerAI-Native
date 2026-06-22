package ai.feltner.nativeapp

import ai.feltner.nativeapp.ui.FeltnerTheme
import ai.feltner.nativeapp.ui.LoginScreen
import ai.feltner.nativeapp.ui.MainScreen
import ai.feltner.nativeapp.ui.ServersScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Root of the Native. Owns the [FeltnerNativeController] and routes between the server
 * picker, login, and the signed-in main shell based on [NativeAppState.screen].
 */
@Composable
@Preview
fun App() {
    val scope = rememberCoroutineScope()
    val vm = remember { FeltnerNativeController(scope) }
    val state by vm.state.collectAsState()

    FeltnerTheme(theme = state.themePref) {
        val snackbar = remember { SnackbarHostState() }

        LaunchedEffect(state.error) {
            state.error?.let {
                snackbar.showSnackbar(it)
                vm.dismissError()
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                when (val screen = state.screen) {
                    is NativeRoute.Servers -> ServersScreen(state, vm, padding)
                    is NativeRoute.Login -> LoginScreen(screen, state, vm, padding)
                    is NativeRoute.Main -> MainScreen(state, vm, padding)
                }
            }
        }
    }
}
