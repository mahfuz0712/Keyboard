package com.mux.android.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun onOff(v: Boolean) = if (v) "ON" else "OFF"

/** TUNE tab: look and behaviour of the keyboard. */
@Composable
fun TuneScreen(tablet: Boolean) {
    val c = Hud.colors

    val look: @Composable (Modifier) -> Unit = { m ->
        HudPanel(m, "LOOK") {
            SettingRow("COLOR THEME", null, if (KeyPrefs.amber) "AMBER" else "CYAN", KeyKind.ACCENT) { KeyPrefs.flip("amber") }
            SettingRow("NUMBER ROW", "Digits above the letters", onOff(KeyPrefs.numberRow)) { KeyPrefs.flip("numberRow") }
            SettingRow("KEY PREVIEW", "Big bubble on key press", onOff(KeyPrefs.preview)) { KeyPrefs.flip("preview") }
            Spacer(Modifier.height(4.dp))
            StepRow("KEY HEIGHT", KeyPrefs.keyHeight.toString() + " DP", { KeyPrefs.nudgeHeight(-4) }, { KeyPrefs.nudgeHeight(4) })
        }
    }

    val feel: @Composable (Modifier) -> Unit = { m ->
        HudPanel(m, "FEEL") {
            SettingRow("VIBRATION", "Tap feedback", onOff(KeyPrefs.haptic)) { KeyPrefs.flip("haptic") }
            SettingRow("KEY SOUND", null, onOff(KeyPrefs.sound)) { KeyPrefs.flip("sound") }
            Spacer(Modifier.height(4.dp))
            StepRow("LONG-PRESS", KeyPrefs.longPress.toString() + " MS", { KeyPrefs.nudgeLongPress(-50) }, { KeyPrefs.nudgeLongPress(50) })
        }
    }

    val typing: @Composable (Modifier) -> Unit = { m ->
        HudPanel(m, "TYPING") {
            SettingRow("BANGLA", "Adds the বাং language key", onOff(KeyPrefs.bangla)) { KeyPrefs.flip("bangla") }
            SettingRow("AUTO-CAPITAL", "Capital at sentence start", onOff(KeyPrefs.autoCap)) { KeyPrefs.flip("autoCap") }
            SettingRow("DOUBLE SPACE", "Space twice = full stop", onOff(KeyPrefs.doubleSpace)) { KeyPrefs.flip("doubleSpace") }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (tablet) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    look(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    feel(Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    typing(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Note()
                }
            }
        } else {
            look(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            feel(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            typing(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Note()
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Note() {
    val c = Hud.colors
    HudPanel(Modifier.fillMaxWidth(), "NOTE") {
        HudText("Changes apply the next time the keyboard opens. Use the TEST tab to try them.", color = c.dim, size = 12.sp)
    }
}

@Composable
private fun StepRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = Hud.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        HudText(label, color = c.text, size = 12.sp, modifier = Modifier.weight(1f))
        HudButton("−", Modifier.width(44.dp).height(38.dp), KeyKind.OP, fontSize = 16.sp) { onMinus() }
        HudText(value, color = c.accent, size = 11.sp, bold = true, modifier = Modifier.width(64.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
        HudButton("+", Modifier.width(44.dp).height(38.dp), KeyKind.OP, fontSize = 16.sp) { onPlus() }
    }
}
