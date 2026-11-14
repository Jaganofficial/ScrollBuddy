package com.scrollbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrollbuddy.history.HistoryStore
import com.scrollbuddy.settings.BuddySettings
import com.scrollbuddy.ui.HistoryScreen
import com.scrollbuddy.ui.HomeScreen
import com.scrollbuddy.ui.OnboardingScreen
import com.scrollbuddy.ui.theme.ScrollBuddyTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = BuddySettings(this)
        val history = HistoryStore(this)
        setContent {
            ScrollBuddyTheme {
                App(settings, history)
            }
        }
    }
}

private enum class Screen(@param:StringRes val title: Int, @param:DrawableRes val icon: Int) {
    Home(R.string.tab_home, R.drawable.ic_home),
    History(R.string.tab_history, R.drawable.ic_chart),
}

@Composable
private fun App(settings: BuddySettings, history: HistoryStore) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    val prefs by settings.values.collectAsStateWithLifecycle(initialValue = remember { settings.read() })
    val entries by history.entries.collectAsStateWithLifecycle(initialValue = remember { history.read() })
    val address = prefs.address(LocalResources.current)

    if (!prefs.onboarded) {
        OnboardingScreen(prefs, settings, onDone = settings::setOnboarded)
        return
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach {
                    NavigationBarItem(
                        selected = it == screen,
                        onClick = { screen = it },
                        icon = { Icon(painterResource(it.icon), contentDescription = null) },
                        label = { Text(stringResource(it.title)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (screen) {
            Screen.Home -> HomeScreen(prefs, settings, entries, address, modifier)
            Screen.History -> HistoryScreen(entries, address, onClear = history::clear, modifier = modifier)
        }
    }
}
