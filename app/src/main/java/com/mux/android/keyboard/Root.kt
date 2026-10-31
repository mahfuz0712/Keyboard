package com.mux.android.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Tab(val label: String, val title: String) {
    SETUP("SETUP", "SETUP"),
    TUNE("TUNE", "TUNING"),
    TEST("TEST", "TYPING TEST"),
    SYS("SYS", "SYSTEM")
}

@Composable
fun KeyboardRoot() {
    val ctx = LocalContext.current
    KeyboardTheme(if (KeyPrefs.amber) AmberPalette else CyanPalette) {
        var update by remember { mutableStateOf<UpdateInfo?>(null) }
        LaunchedEffect(Unit) { if (isOnline(ctx)) update = checkUpdate(ctx).second }

        var tabIndex by rememberSaveable { mutableIntStateOf(0) }
        val tablet = LocalConfiguration.current.screenWidthDp >= 600
        val current = Tab.values()[tabIndex]

        val screen: @Composable () -> Unit = {
            when (current) {
                Tab.SETUP -> SetupScreen(tablet) { tabIndex = Tab.TEST.ordinal }
                Tab.TUNE -> TuneScreen(tablet)
                Tab.TEST -> TestScreen(tablet)
                Tab.SYS -> SystemScreen(tablet) { update = it }
            }
        }

        update?.let { u ->
            UpdateDialog(
                u, appVersion(ctx),
                onUpdate = { Util.open(ctx, u.apkUrl); update = null },
                onCancel = { update = null }
            )
        }

        HudBackground(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
                if (tablet) {
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.width(92.dp).fillMaxHeight()) {
                            for (t in Tab.values()) {
                                HudButton(
                                    label = t.label,
                                    modifier = Modifier.fillMaxWidth().height(60.dp),
                                    kind = KeyKind.OP,
                                    selected = t == current,
                                    fontSize = 14.sp
                                ) { tabIndex = t.ordinal }
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Header(current.title, Modifier.fillMaxWidth().height(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.weight(1f).fillMaxWidth()) { screen() }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Header(current.title, Modifier.fillMaxWidth().height(34.dp))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.weight(1f).fillMaxWidth()) { screen() }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth().height(46.dp)) {
                            for (t in Tab.values()) {
                                HudButton(
                                    label = t.label,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    kind = KeyKind.OP,
                                    selected = t == current,
                                    fontSize = 12.sp
                                ) { tabIndex = t.ordinal }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, modifier: Modifier) {
    val c = Hud.colors
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HudText("◈ KEYBOARD", color = c.primary, size = 16.sp, bold = true)
        HudText(title, color = c.dim, size = 11.sp)
    }
}
