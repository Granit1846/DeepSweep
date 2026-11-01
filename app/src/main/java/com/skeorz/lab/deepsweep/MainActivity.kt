package com.skeorz.lab.deepsweep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skeorz.lab.deepsweep.game.Difficulty
import com.skeorz.lab.deepsweep.game.Mode
import com.skeorz.lab.deepsweep.game.Session
import com.skeorz.lab.deepsweep.meta.Store
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import com.skeorz.lab.deepsweep.ui.AchievementsScreen
import com.skeorz.lab.deepsweep.ui.DifficultyScreen
import com.skeorz.lab.deepsweep.ui.PackOpening
import com.skeorz.lab.deepsweep.ui.GameScreen
import com.skeorz.lab.deepsweep.ui.MenuScreen
import com.skeorz.lab.deepsweep.ui.MissionsScreen
import com.skeorz.lab.deepsweep.ui.ModesScreen
import com.skeorz.lab.deepsweep.ui.ProfileScreen
import com.skeorz.lab.deepsweep.ui.RecordsScreen
import com.skeorz.lab.deepsweep.ui.Screen
import com.skeorz.lab.deepsweep.ui.SettingsScreen
import com.skeorz.lab.deepsweep.ui.ShopScreen
import com.skeorz.lab.deepsweep.ui.palette
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private var session: Session? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = Store(filesDir)
        // cheats and Premium exist only in the test build (BuildConfig flags); release code has neither
        val daily = store.dailyLogin()
        store.refreshMissions()
        store.checkAchievements() // new tiers may be due after an update
        store.toasts.clear()
        setContent { App(daily) }
    }

    override fun onPause() {
        super.onPause()
        session?.save()
        store.save()
    }

    @Composable
    private fun App(daily: Int?) {
        store.rev
        val p = palette(store.d.theme)
        val light = store.d.theme == "theme_arctic"
        val scheme = if (light) lightColorScheme(
            primary = p.accent, onPrimary = Color.White, background = p.bg, onBackground = p.text,
            surface = p.panel, onSurface = p.text, surfaceVariant = p.hidden.copy(alpha = 0.5f), error = p.danger,
            surfaceContainerHigh = p.panel, onSurfaceVariant = p.text,
        ) else darkColorScheme(
            primary = p.accent, onPrimary = p.bg, background = p.bg, onBackground = p.text,
            surface = p.panel, onSurface = p.text, surfaceVariant = p.hidden, error = p.danger,
            surfaceContainerHigh = p.panel, onSurfaceVariant = p.text,
        )
        var screen by remember { mutableStateOf(Screen.MENU) }
        var game by remember { mutableStateOf<Session?>(null) }
        var dailyShown by remember { mutableStateOf(daily == null) }

        fun start(mode: Mode, fresh: Boolean) {
            store.d.lastMode = mode.name
            val diff = runCatching { Difficulty.valueOf(store.d.endlessDifficulty) }.getOrDefault(Difficulty.NORMAL)
            game = Session.load(mode, store, filesDir, fresh, diff).also { session = it }
        }

        // cards of the oldest sealed pack while its opening window is shown
        var packCards by remember { mutableStateOf<List<String>?>(null) }
        var packAll by remember { mutableStateOf(false) }   // the window shows every remaining pack at once
        var packAuto by remember { mutableStateOf(false) }  // opened from «Открыть ещё/все»: tear without asking
        var packKey by remember { mutableIntStateOf(0) }
        val openPack: () -> Unit = { store.d.packs.firstOrNull()?.let { packCards = it; packAll = false; packAuto = false; packKey++ } }

        fun exitGame() { game = null; session = null; screen = Screen.MENU; store.changed() }

        /** A new endless map first asks for the difficulty on its own screen. */
        fun play(mode: Mode, fresh: Boolean) {
            if (mode == Mode.ENDLESS && (fresh || !Session.canContinue(filesDir, mode))) {
                game = null; session = null; screen = Screen.DIFFICULTY
            } else start(mode, fresh)
        }

        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(LocalContentColor provides p.text) {
            Box(Modifier.fillMaxSize().background(p.bg).systemBarsPadding()) {
                val g = game
                if (g != null) {
                    GameScreen(g, store, onExit = ::exitGame, onRestart = { play(g.mode, true) }, onOpenPack = openPack, paused = packCards != null)
                } else {
                    val back = { screen = Screen.MENU }
                    when (screen) {
                        Screen.MENU -> {
                            val last = store.lastPlayable()
                            MenuScreen(store, Session.canContinue(filesDir, last), onPlay = {
                                if (!store.d.tutorialDone && !Session.canContinue(filesDir, last)) start(Mode.TUTORIAL, true)
                                else play(last, false)
                            }, go = { screen = it }, onOpenPack = openPack)
                        }
                        Screen.MODES -> ModesScreen(store, { Session.canContinue(filesDir, it) }, ::play, back)
                        Screen.DIFFICULTY -> DifficultyScreen(store, onPick = {
                            store.d.endlessDifficulty = it.name
                            start(Mode.ENDLESS, true)
                        }, onBack = { screen = Screen.MODES })
                        Screen.MISSIONS -> MissionsScreen(store, back)
                        Screen.ACHIEVEMENTS -> AchievementsScreen(store, back)
                        Screen.SHOP -> ShopScreen(store, back)
                        Screen.PROFILE -> ProfileScreen(store, back)
                        Screen.RECORDS -> RecordsScreen(store, Mode.entries.associateWith { Session.savedScore(filesDir, it) }, store.lastPlayable(), back)
                        Screen.SETTINGS -> SettingsScreen(store, onBack = back)
                    }
                }
                Toasts(Modifier.align(Alignment.BottomCenter).padding(bottom = if (g != null) 90.dp else 24.dp))
                packCards?.let { cards ->
                    key(packKey) {
                        PackOpening(
                            cards, autoTear = packAuto, more = store.d.packs.size,
                            onTear = { if (packAll) store.openAllPacks() else store.openPack() },
                            onDone = { packCards = null },
                            onMore = { store.d.packs.firstOrNull()?.let { packCards = it; packAll = false; packAuto = true; packKey++ } },
                            onAll = { packCards = store.d.packs.flatten(); packAll = true; packAuto = true; packKey++ },
                        )
                    }
                }
            }
            }
            if (!dailyShown && daily != null) AlertDialog(
                onDismissRequest = { dailyShown = true },
                title = { Text("Ежедневная вахта · день ${store.d.streak}") },
                text = { Text("Вы получаете $daily 💎. Заходите завтра — награда будет больше.") },
                confirmButton = { TextButton(onClick = { dailyShown = true }) { Text("Забрать") } },
            )
        }
    }

    @Composable
    private fun Toasts(modifier: Modifier) {
        val msg = store.toasts.firstOrNull() ?: return
        LaunchedEffect(msg, store.toasts.size) {
            delay(if (store.toasts.size > 3) 700 else 1800)
            store.toasts.removeFirstOrNull()
        }
        Text(
            msg,
            modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)).padding(12.dp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
