package com.example.drivesync

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.drivesync.ui.setup.SetupScreen
import com.example.drivesync.ui.sync.SyncScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Sync)

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.lastOrNull() == Setup) {
                backStack.clear()
                backStack.add(Sync)
            }
        },
        entryProvider = entryProvider {
            entry<Setup> {
                SetupScreen(
                    onSetupComplete = {
                        backStack.clear()
                        backStack.add(Sync)
                    },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
            entry<Sync> {
                SyncScreen(
                    onNavigateToSetup = {
                        backStack.clear()
                        backStack.add(Setup)
                    },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        },
    )
}
