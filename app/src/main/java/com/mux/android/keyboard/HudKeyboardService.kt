package com.mux.android.keyboard

import android.content.ClipboardManager
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager

/** The actual input method. Owns the state (page / shift / language) and talks to the text field. */
class HudKeyboardService : InputMethodService(), KeyboardView.Listener {

    private var kv: KeyboardView? = null

    private var bangla = false          // current language
    private var page = Page.EN
    private var shift = 0               // 0 off, 1 once, 2 lock
    private var lastShiftTap = 0L
    private var lastSpace = 0L
    private var enterLabel = ""
    private var textField = true

    override fun onCreate() {
        super.onCreate()
        KeyPrefs.init(this)
    }

    override fun onCreateInputView(): View {
        val v = KeyboardView(this)
        v.listener = this
        kv = v
        refresh()
        return v
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!KeyPrefs.bangla) bangla = false

        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        textField = cls == InputType.TYPE_CLASS_TEXT
        page = when (cls) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> Page.SYM1
            else -> letterPage()
        }

        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        enterLabel = if (noEnterAction) "" else when (action) {
            EditorInfo.IME_ACTION_GO -> "GO"
            EditorInfo.IME_ACTION_SEARCH -> "FIND"
            EditorInfo.IME_ACTION_SEND -> "SEND"
            EditorInfo.IME_ACTION_NEXT -> "NEXT"
            EditorInfo.IME_ACTION_DONE -> "DONE"
            else -> ""
        }

        shift = 0
        autoShift()
        refresh()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (autoShift()) refresh()
    }

    // ---------------------------------------------------------------- state helpers

    private fun letterPage(): Page = if (bangla && KeyPrefs.bangla) Page.BN1 else Page.EN

    private fun langLabel(): String = if (bangla && KeyPrefs.bangla) "বাং" else "EN"

    private fun refresh() {
        kv?.setState(page, shift, enterLabel, langLabel(), letterPage())
    }

    /** Capital letter at the start of a sentence. Returns true if the shift state changed. */
    private fun autoShift(): Boolean {
        if (!KeyPrefs.autoCap || bangla || shift == 2 || !textField) return false
        val ei = currentInputEditorInfo ?: return false
        val ic = currentInputConnection ?: return false
        val caps = ic.getCursorCapsMode(ei.inputType)
        val want = if (caps != 0) 1 else 0
        if (want == shift) return false
        shift = want
        return true
    }

    // ---------------------------------------------------------------- KeyboardView.Listener

    override fun onChar(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        if (shift == 1) {
            shift = 0
            refresh()
        }
    }

    override fun onDelete() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    override fun onShift(longPress: Boolean) {
        if (page != Page.EN) return
        val now = SystemClock.uptimeMillis()
        shift = when {
            longPress -> 2
            shift == 2 -> 0
            now - lastShiftTap < 350 -> 2
            shift == 1 -> 0
            else -> 1
        }
        lastShiftTap = if (longPress) 0L else now
        refresh()
    }

    override fun onEnter() {
        val ei = currentInputEditorInfo
        val ic = currentInputConnection
        if (ei != null && ic != null) {
            val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
            val noEnterAction = (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
            if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action)
                return
            }
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }

    override fun onSpace() {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        if (KeyPrefs.doubleSpace && now - lastSpace < 400 && textField) {
            val before = ic.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 && before[1] == ' ' &&
                !before[0].isWhitespace() && ".,!?;:।".indexOf(before[0]) < 0
            ) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(if (bangla) "। " else ". ", 1)
                lastSpace = 0L
                return
            }
        }
        ic.commitText(" ", 1)
        lastSpace = now
        if (page == Page.SYM1 || page == Page.SYM2) {
            if (textField) {
                page = letterPage()
                refresh()
            }
        }
    }

    override fun onPage(target: Page) {
        page = target
        if (page == Page.EN && shift == 1) shift = 0
        refresh()
    }

    override fun onLang() {
        if (KeyPrefs.bangla) {
            bangla = !bangla
            shift = 0
            page = letterPage()
            if (!bangla) autoShift()
            refresh()
        } else {
            onPicker()
        }
    }

    override fun onPicker() {
        try {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        } catch (e: Exception) {
        }
    }

    override fun onMoveCursor(dir: Int) {
        sendDownUpKeyEvents(if (dir > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
    }

    override fun onPaste() {
        val ic = currentInputConnection ?: return
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)
            if (!text.isNullOrEmpty()) ic.commitText(text, 1)
        } catch (e: Exception) {
        }
    }

    override fun onHide() {
        requestHideSelf(0)
    }
}
