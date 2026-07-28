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
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
        },
        entryProvider = entryProvider {
            entry<Setup> {
                SetupScreen(
                    onSetupComplete = {
                        if (backStack.size > 1) {
                            backStack.removeLastOrNull()
                        } else {
                            backStack.clear()
                            backStack.add(Sync)
                        }
                    },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
            entry<Sync> {
                SyncScreen(
                    onNavigateToSetup = {
                        if (backStack.lastOrNull() != Setup) {
                            backStack.add(Setup)
                        }
                    },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        },
    )
}
