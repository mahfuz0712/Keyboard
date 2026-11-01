package com.mux.android.keyboard

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private fun isEnabled(ctx: Context): Boolean = try {
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.enabledInputMethodList.any { it.packageName == ctx.packageName }
} catch (e: Exception) {
    false
}

private fun isSelected(ctx: Context): Boolean = try {
    val cur = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    cur != null && cur.startsWith(ctx.packageName + "/")
} catch (e: Exception) {
    false
}

/** SETUP tab: enable the keyboard, switch to it, go test it. */
@Composable
fun SetupScreen(tablet: Boolean, onTest: () -> Unit) {
    val ctx = LocalContext.current
    val c = Hud.colors
    var poll by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(800)
            poll = poll + 1
        }
    }
    val tick = resumeTick.intValue
    val enabled = remember(tick, poll) { isEnabled(ctx) }
    val selected = remember(tick, poll) { isSelected(ctx) }

    val steps: @Composable (Modifier) -> Unit = { m ->
        HudPanel(m, "ACTIVATION") {
            StepLine("1  ENABLE KEYBOARD", enabled)
            StepLine("2  SELECT AS ACTIVE", selected)
            Spacer(Modifier.height(10.dp))
            HudButton("1 · ENABLE IN SETTINGS", Modifier.fillMaxWidth().height(48.dp), KeyKind.ACCENT, enabled = !enabled, fontSize = 13.sp) {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Util.toast(ctx, "OPEN SETTINGS > LANGUAGE & INPUT")
                }
            }
            HudButton("2 · SWITCH TO KEYBOARD", Modifier.fillMaxWidth().height(48.dp), KeyKind.ACCENT, enabled = enabled && !selected, fontSize = 13.sp) {
                try {
                    (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                } catch (e: Exception) {
                }
            }
            HudButton("3 · TRY IT", Modifier.fillMaxWidth().height(48.dp), KeyKind.GOOD, enabled = selected, fontSize = 13.sp) { onTest() }
        }
    }

    val info: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            HudPanel(Modifier.fillMaxWidth(), "STATUS") {
                HudText(
                    when {
                        selected -> "KEYBOARD ACTIVE"
                        enabled -> "ENABLED · NOT SELECTED YET"
                        else -> "NOT ENABLED"
                    },
                    color = if (selected) c.good else c.accent, size = 15.sp, bold = true
                )
                Spacer(Modifier.height(4.dp))
                HudText(
                    "Android shows a warning that a keyboard can read what you type. That message is the same for every keyboard.",
                    color = c.text, size = 12.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            HudPanel(Modifier.fillMaxWidth(), "PRIVACY") {
                HudText("◈ NO NETWORK ACCESS FROM THE KEYBOARD", color = c.primary, size = 11.sp, bold = true)
                HudText("◈ NOTHING YOU TYPE IS STORED OR LOGGED", color = c.primary, size = 11.sp, bold = true)
                HudText("◈ NO ADS, NO ACCOUNT", color = c.primary, size = 11.sp, bold = true)
                Spacer(Modifier.height(4.dp))
                HudText("Only this settings app can go online, and only to look for a new version.", color = c.dim, size = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            HudPanel(Modifier.fillMaxWidth(), "BANGLA PHONETIC") {
                HudText("Press the EN key to switch to বাং, then type with English letters:", color = c.text, size = 12.sp)
                Spacer(Modifier.height(4.dp))
                HudText("ami banglay gan gai.", color = c.dim, size = 12.sp)
                HudText("আমি বাংলায় গান গাই।", color = c.accent, size = 15.sp, bold = true)
                Spacer(Modifier.height(6.dp))
                HudText("• Strip on top shows other spellings. Tap one to use it.", color = c.text, size = 12.sp)
                HudText("• SPACE keeps the first (highlighted) word.", color = c.text, size = 12.sp)
                HudText("• Capital letters = hard sounds: T D N R S, O I U (use SHIFT).", color = c.text, size = 12.sp)
                HudText("• Hold n for ং ঁ ঃ, hold t for ৎ.", color = c.text, size = 12.sp)
                HudText("• Dot key gives ।", color = c.text, size = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            HudPanel(Modifier.fillMaxWidth(), "HOW TO TYPE") {
                HudText("• HOLD A KEY: accents, symbols, digits", color = c.text, size = 12.sp)
                HudText("• DRAG ON SPACE: move the cursor", color = c.text, size = 12.sp)
                HudText("• DOUBLE-TAP SHIFT: caps lock", color = c.text, size = 12.sp)
                HudText("• HOLD LANGUAGE KEY: switch keyboard", color = c.text, size = 12.sp)
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (tablet) {
            Row(Modifier.fillMaxWidth()) {
                steps(Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                info(Modifier.weight(1f))
            }
        } else {
            steps(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            info(Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StepLine(label: String, done: Boolean) {
    val c = Hud.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        HudText(label, color = c.text, size = 12.sp, modifier = Modifier.weight(1f))
        HudText(if (done) "OK" else "TODO", color = if (done) c.good else c.danger, size = 12.sp, bold = true)
    }
}
