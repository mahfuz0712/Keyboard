package com.mux.android.keyboard

import android.content.ClipboardManager
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager

/**
 * The actual input method. Owns the state (page / shift / language) and talks to the text field.
 *
 * Bangla mode is phonetic: the English QWERTY keys build a word in [buf], the word is shown
 * converted to Bangla as composing text, and the suggestion strip offers other spellings.
 */
class HudKeyboardService : InputMethodService(), KeyboardView.Listener {

    private var kv: KeyboardView? = null

    private var bangla = false          // phonetic Bangla on
    private var page = Page.EN
    private var shift = 0               // 0 off, 1 once, 2 lock
    private var lastShiftTap = 0L
    private var lastSpace = 0L
    private var enterLabel = ""
    private var textField = true
    private var phoneticAllowed = true  // off for password / email / url fields

    private val buf = StringBuilder()   // what the user typed for the current word
    private var best = ""               // Bangla text that will be committed for it
    private var cands: List<String> = emptyList()

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
        resetWord()
        if (!KeyPrefs.bangla) bangla = false

        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        textField = cls == InputType.TYPE_CLASS_TEXT
        phoneticAllowed = textField &&
            variation != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
            variation != InputType.TYPE_TEXT_VARIATION_URI

        page = when (cls) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> Page.SYM1
            else -> Page.EN
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

    override fun onFinishInput() {
        flushWord()
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // The user moved the cursor or selected text: the word being typed is no longer ours.
        if (buf.isNotEmpty()) {
            val moved = candidatesStart != -1 && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)
            if (moved || newSelStart != newSelEnd) {
                resetWord()
                currentInputConnection?.finishComposingText()
            }
        }
        if (autoShift()) refresh()
    }

    // ---------------------------------------------------------------- state helpers

    private fun engineOn(): Boolean = bangla && KeyPrefs.bangla && phoneticAllowed && page == Page.EN

    private fun langLabel(): String = if (bangla && KeyPrefs.bangla) "বাং" else "EN"

    private fun refresh() {
        kv?.setState(page, shift, enterLabel, langLabel(), bangla && KeyPrefs.bangla && phoneticAllowed)
    }

    /** Capital letter at the start of a sentence (English only). Returns true if shift changed. */
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

    // ---------------------------------------------------------------- phonetic word handling

    private fun resetWord() {
        buf.setLength(0)
        best = ""
        cands = emptyList()
        kv?.setSuggestions(emptyList())
    }

    /** Shows the converted word in the field and the candidates in the strip. */
    private fun updateComposing() {
        val ic = currentInputConnection ?: return
        if (buf.isEmpty()) {
            ic.setComposingText("", 1)
            ic.finishComposingText()
            resetWord()
            return
        }
        val s = Phonetic.suggest(buf.toString())
        best = s.best
        cands = s.list
        ic.setComposingText(best, 1)
        kv?.setSuggestions(cands)
    }

    /** Commits the word being typed (as shown) and clears the strip. */
    private fun flushWord() {
        if (buf.isEmpty()) return
        currentInputConnection?.commitText(best, 1)
        resetWord()
    }

    private fun isWordChar(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'

    // ---------------------------------------------------------------- KeyboardView.Listener

    override fun onChar(text: String, alt: Boolean) {
        val ic = currentInputConnection ?: return
        if (engineOn()) {
            if (alt) {
                // chosen from the long-press popup: type exactly this
                flushWord()
                ic.commitText(text, 1)
            } else if (text.length == 1 && isWordChar(text[0])) {
                buf.append(text)
                updateComposing()
            } else {
                flushWord()
                ic.commitText(if (text == ".") "।" else text, 1)
            }
        } else {
            ic.commitText(text, 1)
        }
        if (shift == 1) {
            shift = 0
            refresh()
        }
    }

    override fun onSuggestion(index: Int) {
        val ic = currentInputConnection ?: return
        if (index !in cands.indices) return
        val word = cands[index]
        ic.commitText("$word ", 1)
        resetWord()
        lastSpace = 0L
    }

    override fun onDelete() {
        if (engineOn() && buf.isNotEmpty()) {
            buf.setLength(buf.length - 1)
            updateComposing()
            return
        }
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
        flushWord()
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
        flushWord()
        val now = SystemClock.uptimeMillis()
        if (KeyPrefs.doubleSpace && now - lastSpace < 400 && textField) {
            val before = ic.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 && before[1] == ' ' &&
                !before[0].isWhitespace() && ".,!?;:।".indexOf(before[0]) < 0
            ) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(if (bangla && KeyPrefs.bangla) "। " else ". ", 1)
                lastSpace = 0L
                return
            }
        }
        ic.commitText(" ", 1)
        lastSpace = now
        if ((page == Page.SYM1 || page == Page.SYM2) && textField) {
            page = Page.EN
            refresh()
        }
    }

    override fun onPage(target: Page) {
        flushWord()
        page = target
        if (page == Page.EN && shift == 1) shift = 0
        refresh()
    }

    override fun onLang() {
        flushWord()
        if (KeyPrefs.bangla) {
            bangla = !bangla
            shift = 0
            page = Page.EN
            if (!bangla) autoShift()
            refresh()
        } else {
            onPicker()
        }
    }

    override fun onPicker() {
        flushWord()
        try {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        } catch (e: Exception) {
        }
    }

    override fun onMoveCursor(dir: Int) {
        flushWord()
        sendDownUpKeyEvents(if (dir > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
    }

    override fun onPaste() {
        flushWord()
        val ic = currentInputConnection ?: return
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)
            if (!text.isNullOrEmpty()) ic.commitText(text, 1)
        } catch (e: Exception) {
        }
    }

    override fun onHide() {
        flushWord()
        requestHideSelf(0)
    }
}
