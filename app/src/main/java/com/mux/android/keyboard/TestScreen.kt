package com.mux.android.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** TEST tab: text fields of different kinds to try the keyboard. */
@Composable
fun TestScreen(tablet: Boolean) {
    var note by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val c = Hud.colors

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HudPanel(Modifier.fillMaxWidth(), "TEXT") {
            TestField(note, "Type here. Try English and Bangla.", 120.dp, false, KeyboardType.Text, false) { note = it }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().height(38.dp)) {
                HudText("${note.length} CHARACTERS", color = c.dim, size = 11.sp, modifier = Modifier.weight(1f))
                HudButton("CLEAR", Modifier.width(90.dp).height(38.dp), KeyKind.DANGER, fontSize = 11.sp) { note = "" }
            }
        }
        Spacer(Modifier.height(8.dp))
        HudPanel(Modifier.fillMaxWidth(), "NUMBER FIELD") {
            TestField(number, "Number pad appears here", 46.dp, true, KeyboardType.Number, false) { number = it }
        }
        Spacer(Modifier.height(8.dp))
        HudPanel(Modifier.fillMaxWidth(), "PASSWORD FIELD") {
            TestField(pass, "Password", 46.dp, true, KeyboardType.Password, true) { pass = it }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TestField(
    value: String,
    hint: String,
    height: Dp,
    singleLine: Boolean,
    type: KeyboardType,
    password: Boolean,
    onChange: (String) -> Unit
) {
    val c = Hud.colors
    val shape = HudShape(8.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(c.primary.copy(alpha = 0.06f), shape)
            .border(1.dp, c.primary.copy(alpha = 0.5f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
    ) {
        if (value.isEmpty()) HudText(hint, color = c.dim, size = 13.sp, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = type),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = TextStyle(color = c.text, fontSize = 15.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(c.primary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
