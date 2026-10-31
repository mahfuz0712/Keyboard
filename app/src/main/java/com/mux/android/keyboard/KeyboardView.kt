package com.mux.android.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** The HUD keyboard surface. Draws every key itself (cut-corner shapes) and handles touch. */
class KeyboardView(ctx: Context) : View(ctx) {

    interface Listener {
        fun onChar(text: String)
        fun onDelete()
        fun onShift(longPress: Boolean)
        fun onEnter()
        fun onSpace()
        fun onPage(target: Page)
        fun onLang()
        fun onPicker()
        fun onMoveCursor(dir: Int)
        fun onPaste()
        fun onHide()
    }

    var listener: Listener? = null

    private class KeyRect(val key: Key, val rect: RectF, val hit: RectF) {
        val path = Path()
    }

    private class Pointer(var kr: KeyRect?, val downX: Float) {
        var longFired = false
        var popup: String = ""
        var popupIdx = -1
        var popupLeft = 0f
        var lastX = downX
        var dragged = false
        var longRun: Runnable? = null
        var repeatRun: Runnable? = null
    }

    private val d = resources.displayMetrics.density
    private val ui = Handler(Looper.getMainLooper())
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val gap = 4f * d
    private val side = 3f * d
    private val stripH = 34f * d

    private var rows: List<List<Key>> = emptyList()
    private val rects = ArrayList<KeyRect>()
    private val pointers = SparseArray<Pointer>()

    private var page = Page.EN
    private var shift = 0
    private var enterLabel = ""
    private var langLabel = "EN"
    private var letterPage = Page.EN
    private var numberRow = true
    private var title = "EN"

    private val pasteRect = RectF()
    private val hideRect = RectF()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * d }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }
    private val gridPaint = Paint().apply { strokeWidth = 1f }
    private val tmpPath = Path()
    private val tmpRect = RectF()

    // colours (cyan / amber HUD)
    private var cBg = 0
    private var cPanel = 0
    private var cPrimary = 0
    private var cDim = 0
    private var cAccent = 0
    private var cText = 0

    init {
        applyTheme()
    }

    private fun applyTheme() {
        if (KeyPrefs.amber) {
            cBg = 0xFF0D0804.toInt(); cPanel = 0xFF1A1008.toInt(); cPrimary = 0xFFFFA726.toInt()
            cDim = 0xFFA6732A.toInt(); cAccent = 0xFF19E3FF.toInt(); cText = 0xFFFFEBD0.toInt()
        } else {
            cBg = 0xFF040A12.toInt(); cPanel = 0xFF08141F.toInt(); cPrimary = 0xFF19E3FF.toInt()
            cDim = 0xFF3C8A99.toInt(); cAccent = 0xFFFFB000.toInt(); cText = 0xFFD6F8FF.toInt()
        }
    }

    private fun keyHeightPx(): Float {
        val land = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val dp = if (land) KeyPrefs.keyHeight * 0.78f else KeyPrefs.keyHeight.toFloat()
        return dp * d
    }

    /** Called by the service whenever the page / shift / editor changes. */
    fun setState(page: Page, shift: Int, enterLabel: String, langLabel: String, letterPage: Page) {
        this.page = page
        this.shift = shift
        this.enterLabel = enterLabel
        this.langLabel = langLabel
        this.letterPage = letterPage
        this.title = langLabel
        applyTheme()
        numberRow = KeyPrefs.numberRow
        rows = Layouts.rows(Layouts.Ctx(page, shift, numberRow, enterLabel, langLabel, letterPage))
        cancelAll()
        requestLayout()
        layoutKeys()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val n = if (rows.isEmpty()) 5 else rows.size
        val h = stripH + n * keyHeightPx() + (n + 1) * gap
        setMeasuredDimension(w, h.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutKeys()
    }

    private fun layoutKeys() {
        rects.clear()
        val w = width.toFloat()
        if (w <= 0f || rows.isEmpty()) return
        val kh = (height - stripH - (rows.size + 1) * gap) / rows.size
        val cut = min(6f * d, kh * 0.22f)
        var y = stripH + gap
        for (row in rows) {
            var sum = 0f
            for (k in row) sum += k.weight
            val usable = w - 2 * side - gap * (row.size - 1)
            var x = side
            for (k in row) {
                val kw = usable * k.weight / sum
                if (k.type != KT.GAP) {
                    val r = RectF(x, y, x + kw, y + kh)
                    val hit = RectF(x - gap / 2, y - gap / 2, x + kw + gap / 2, y + kh + gap / 2)
                    val kr = KeyRect(k, r, hit)
                    cutPath(kr.path, r, cut)
                    rects.add(kr)
                }
                x += kw + gap
            }
            y += kh + gap
        }
        // strip buttons
        val bh = stripH - 8f * d
        val bw = 62f * d
        hideRect.set(w - side - bw, 4f * d, w - side, 4f * d + bh)
        pasteRect.set(hideRect.left - gap - bw, 4f * d, hideRect.left - gap, 4f * d + bh)
    }

    private fun cutPath(p: Path, r: RectF, c: Float) {
        p.reset()
        p.moveTo(r.left + c, r.top)
        p.lineTo(r.right, r.top)
        p.lineTo(r.right, r.bottom - c)
        p.lineTo(r.right - c, r.bottom)
        p.lineTo(r.left, r.bottom)
        p.lineTo(r.left, r.top + c)
        p.close()
    }

    // ---------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(cBg)
        drawGrid(canvas)
        drawStrip(canvas)
        val pressedKeys = HashSet<KeyRect>()
        for (i in 0 until pointers.size()) pointers.valueAt(i).kr?.let { pressedKeys.add(it) }
        for (kr in rects) drawKey(canvas, kr, pressedKeys.contains(kr))
        for (i in 0 until pointers.size()) {
            val p = pointers.valueAt(i)
            val kr = p.kr ?: continue
            if (p.popup.isNotEmpty()) drawPopup(canvas, kr, p)
            else if (KeyPrefs.preview && kr.key.type == KT.CHAR) drawPreview(canvas, kr)
        }
    }

    private fun drawGrid(canvas: Canvas) {
        gridPaint.color = (cPrimary and 0x00FFFFFF) or 0x14000000
        val step = 24f * d
        var x = 0f
        while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint); x += step }
        var y = 0f
        while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, gridPaint); y += step }
    }

    private fun drawStrip(canvas: Canvas) {
        monoPaint.textSize = 13f * d
        monoPaint.textAlign = Paint.Align.LEFT
        monoPaint.color = cPrimary
        canvas.drawText("◈ " + title, side + 4f * d, stripH / 2f + 5f * d, monoPaint)
        monoPaint.textSize = 9f * d
        monoPaint.color = cDim
        val pageName = when (page) {
            Page.EN -> "LETTERS"
            Page.SYM1 -> "SYMBOLS 1/2"
            Page.SYM2 -> "SYMBOLS 2/2"
            Page.BN1 -> "BANGLA 1/3"
            Page.BN2 -> "BANGLA 2/3"
            Page.BN3 -> "BANGLA 3/3"
        }
        canvas.drawText(pageName, side + 52f * d, stripH / 2f + 4f * d, monoPaint)
        drawSmallButton(canvas, pasteRect, "PASTE", cPrimary)
        drawSmallButton(canvas, hideRect, "HIDE", cAccent)
    }

    private fun drawSmallButton(canvas: Canvas, r: RectF, label: String, color: Int) {
        cutPath(tmpPath, r, 4f * d)
        fill.color = (color and 0x00FFFFFF) or 0x14000000
        canvas.drawPath(tmpPath, fill)
        stroke.color = (color and 0x00FFFFFF) or 0x99000000.toInt()
        canvas.drawPath(tmpPath, stroke)
        monoPaint.textSize = 10f * d
        monoPaint.textAlign = Paint.Align.CENTER
        monoPaint.color = color
        canvas.drawText(label, r.centerX(), r.centerY() + 3.5f * d, monoPaint)
    }

    private fun keyColor(k: Key): Int = when (k.type) {
        KT.ENTER -> cAccent
        KT.CHAR -> cText
        KT.SPACE -> cPrimary
        else -> cPrimary
    }

    private fun drawKey(canvas: Canvas, kr: KeyRect, pressed: Boolean) {
        val k = kr.key
        val base = keyColor(k)
        val active = (k.type == KT.SHIFT && shift != 0)
        val alpha = if (pressed) 0x55 else if (active) 0x40 else 0x12
        fill.color = (base and 0x00FFFFFF) or (alpha shl 24)
        canvas.drawPath(kr.path, fill)
        val sAlpha = if (k.type == KT.CHAR) 0x66 else 0xAA
        stroke.color = ((if (k.type == KT.CHAR) cPrimary else base) and 0x00FFFFFF) or (sAlpha shl 24)
        canvas.drawPath(kr.path, stroke)

        val r = kr.rect
        when (k.type) {
            KT.SHIFT -> drawShiftIcon(canvas, r, base)
            KT.DEL -> drawDeleteIcon(canvas, r, base)
            KT.ENTER -> if (k.label.isEmpty()) drawEnterIcon(canvas, r, base) else drawLabel(canvas, kr, k.label, base, 0.30f, true)
            KT.SPACE -> drawSpaceBar(canvas, r)
            KT.LANG -> drawLabel(canvas, kr, k.label, cPrimary, 0.30f, false)
            KT.PAGE -> drawLabel(canvas, kr, k.label, cPrimary, 0.30f, true)
            KT.CHAR -> {
                drawLabel(canvas, kr, k.label, cText, 0.40f, false)
                if (k.hint.isNotEmpty()) {
                    textPaint.typeface = Typeface.MONOSPACE
                    textPaint.textSize = r.height() * 0.22f
                    textPaint.color = cDim
                    textPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(k.hint, r.right - 5f * d, r.top + r.height() * 0.28f, textPaint)
                    textPaint.textAlign = Paint.Align.CENTER
                    textPaint.typeface = Typeface.DEFAULT_BOLD
                }
            }
            KT.GAP -> {}
        }
    }

    private fun drawLabel(canvas: Canvas, kr: KeyRect, label: String, color: Int, scale: Float, mono: Boolean) {
        val r = kr.rect
        textPaint.typeface = if (mono) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD
        textPaint.isFakeBoldText = true
        textPaint.color = color
        textPaint.textAlign = Paint.Align.CENTER
        var size = r.height() * scale
        textPaint.textSize = size
        val maxW = r.width() * 0.88f
        val tw = textPaint.measureText(label)
        if (tw > maxW) {
            size = size * maxW / tw
            textPaint.textSize = size
        }
        val fm = textPaint.fontMetrics
        canvas.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, textPaint)
        textPaint.typeface = Typeface.DEFAULT_BOLD
    }

    private fun drawSpaceBar(canvas: Canvas, r: RectF) {
        iconPaint.color = (cPrimary and 0x00FFFFFF) or 0x99000000.toInt()
        val w = r.width() * 0.28f
        val y = r.centerY()
        canvas.drawLine(r.centerX() - w, y, r.centerX() + w, y, iconPaint)
        canvas.drawLine(r.centerX() - w, y - 4f * d, r.centerX() - w, y, iconPaint)
        canvas.drawLine(r.centerX() + w, y - 4f * d, r.centerX() + w, y, iconPaint)
    }

    private fun drawShiftIcon(canvas: Canvas, r: RectF, color: Int) {
        val s = r.height() * 0.22f
        val cx = r.centerX()
        val cy = r.centerY() - (if (shift == 2) 1.5f * d else 0f)
        val p = tmpPath
        p.reset()
        p.moveTo(cx, cy - s)
        p.lineTo(cx + s, cy)
        p.lineTo(cx + s * 0.45f, cy)
        p.lineTo(cx + s * 0.45f, cy + s * 0.9f)
        p.lineTo(cx - s * 0.45f, cy + s * 0.9f)
        p.lineTo(cx - s * 0.45f, cy)
        p.lineTo(cx - s, cy)
        p.close()
        iconPaint.color = color
        if (shift != 0) {
            fill.color = color
            canvas.drawPath(p, fill)
        }
        canvas.drawPath(p, iconPaint)
        if (shift == 2) canvas.drawLine(cx - s * 0.6f, cy + s * 1.3f, cx + s * 0.6f, cy + s * 1.3f, iconPaint)
    }

    private fun drawDeleteIcon(canvas: Canvas, r: RectF, color: Int) {
        val s = r.height() * 0.24f
        val cx = r.centerX()
        val cy = r.centerY()
        val p = tmpPath
        p.reset()
        p.moveTo(cx - s * 1.3f, cy)
        p.lineTo(cx - s * 0.5f, cy - s * 0.85f)
        p.lineTo(cx + s * 1.2f, cy - s * 0.85f)
        p.lineTo(cx + s * 1.2f, cy + s * 0.85f)
        p.lineTo(cx - s * 0.5f, cy + s * 0.85f)
        p.close()
        iconPaint.color = color
        canvas.drawPath(p, iconPaint)
        canvas.drawLine(cx - s * 0.1f, cy - s * 0.35f, cx + s * 0.7f, cy + s * 0.35f, iconPaint)
        canvas.drawLine(cx + s * 0.7f, cy - s * 0.35f, cx - s * 0.1f, cy + s * 0.35f, iconPaint)
    }

    private fun drawEnterIcon(canvas: Canvas, r: RectF, color: Int) {
        val s = r.height() * 0.22f
        val cx = r.centerX()
        val cy = r.centerY()
        iconPaint.color = color
        canvas.drawLine(cx + s, cy - s, cx + s, cy + s * 0.4f, iconPaint)
        canvas.drawLine(cx + s, cy + s * 0.4f, cx - s, cy + s * 0.4f, iconPaint)
        canvas.drawLine(cx - s, cy + s * 0.4f, cx - s * 0.35f, cy - s * 0.2f, iconPaint)
        canvas.drawLine(cx - s, cy + s * 0.4f, cx - s * 0.35f, cy + s, iconPaint)
    }

    private fun drawPreview(canvas: Canvas, kr: KeyRect) {
        val r = kr.rect
        val w = max(r.width() * 1.25f, 44f * d)
        val h = r.height() * 1.15f
        var left = r.centerX() - w / 2f
        left = left.coerceIn(2f * d, width - w - 2f * d)
        var top = r.top - h - 4f * d
        if (top < 0f) top = 0f
        tmpRect.set(left, top, left + w, top + h)
        cutPath(tmpPath, tmpRect, 8f * d)
        fill.color = cPanel
        canvas.drawPath(tmpPath, fill)
        fill.color = (cPrimary and 0x00FFFFFF) or 0x30000000
        canvas.drawPath(tmpPath, fill)
        stroke.color = cPrimary
        canvas.drawPath(tmpPath, stroke)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.color = cText
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = h * 0.52f
        val fm = textPaint.fontMetrics
        canvas.drawText(kr.key.label, tmpRect.centerX(), tmpRect.centerY() - (fm.ascent + fm.descent) / 2f, textPaint)
    }

    private fun popupItems(s: String): List<String> {
        val list = ArrayList<String>()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = Character.charCount(cp)
            list.add(s.substring(i, i + n))
            i += n
        }
        return list
    }

    private fun drawPopup(canvas: Canvas, kr: KeyRect, p: Pointer) {
        val items = popupItems(p.popup)
        val r = kr.rect
        val cell = max(r.width(), 38f * d)
        val h = r.height() * 1.05f
        val total = cell * items.size
        var top = r.top - h - 4f * d
        if (top < 0f) top = 0f
        tmpRect.set(p.popupLeft, top, p.popupLeft + total, top + h)
        cutPath(tmpPath, tmpRect, 8f * d)
        fill.color = cPanel
        canvas.drawPath(tmpPath, fill)
        stroke.color = cAccent
        canvas.drawPath(tmpPath, stroke)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = h * 0.46f
        val fm = textPaint.fontMetrics
        for ((i, s) in items.withIndex()) {
            val l = p.popupLeft + i * cell
            if (i == p.popupIdx) {
                fill.color = (cAccent and 0x00FFFFFF) or 0x55000000
                canvas.drawRect(l + 2f * d, top + 2f * d, l + cell - 2f * d, top + h - 2f * d, fill)
            }
            textPaint.color = if (i == p.popupIdx) cAccent else cText
            canvas.drawText(s, l + cell / 2f, top + h / 2f - (fm.ascent + fm.descent) / 2f, textPaint)
        }
    }

    // ---------------------------------------------------------------- feedback

    private fun feedback(type: KT) {
        if (KeyPrefs.haptic) {
            performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        }
        if (KeyPrefs.sound) {
            val fx = when (type) {
                KT.DEL -> AudioManager.FX_KEYPRESS_DELETE
                KT.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                KT.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio.playSoundEffect(fx)
        }
    }

    // ---------------------------------------------------------------- touch

    private fun findKey(x: Float, y: Float): KeyRect? {
        for (kr in rects) if (kr.hit.contains(x, y)) return kr
        var best: KeyRect? = null
        var bestD = Float.MAX_VALUE
        for (kr in rects) {
            val dx = kr.rect.centerX() - x
            val dy = kr.rect.centerY() - y
            val dist = dx * dx + dy * dy
            if (dist < bestD) { bestD = dist; best = kr }
        }
        return if (bestD < (60f * d) * (60f * d)) best else null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                up(e.getPointerId(i), e.getX(i))
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun down(id: Int, x: Float, y: Float) {
        if (y < stripH) {
            if (pasteRect.contains(x, y)) { feedback(KT.CHAR); listener?.onPaste() }
            else if (hideRect.contains(x, y)) { feedback(KT.CHAR); listener?.onHide() }
            return
        }
        val kr = findKey(x, y) ?: return
        val p = Pointer(kr, x)
        pointers.put(id, p)
        feedback(kr.key.type)
        when (kr.key.type) {
            KT.DEL -> {
                p.longFired = true
                listener?.onDelete()
                val rep = object : Runnable {
                    override fun run() {
                        listener?.onDelete()
                        ui.postDelayed(this, 55)
                    }
                }
                p.repeatRun = rep
                ui.postDelayed(rep, 380)
            }
            KT.CHAR, KT.SHIFT, KT.LANG -> scheduleLong(p)
            else -> {}
        }
        invalidate()
    }

    private fun scheduleLong(p: Pointer) {
        val run = Runnable { fireLong(p) }
        p.longRun = run
        ui.postDelayed(run, KeyPrefs.longPress.toLong())
    }

    private fun fireLong(p: Pointer) {
        val kr = p.kr ?: return
        when (kr.key.type) {
            KT.CHAR -> {
                if (kr.key.alts.isNotEmpty()) {
                    p.longFired = true
                    p.popup = kr.key.alts
                    val n = popupItems(p.popup).size
                    val cell = max(kr.rect.width(), 38f * d)
                    var left = kr.rect.centerX() - cell / 2f
                    left = left.coerceIn(2f * d, max(2f * d, width - cell * n - 2f * d))
                    p.popupLeft = left
                    p.popupIdx = ((kr.rect.centerX() - left) / cell).toInt().coerceIn(0, n - 1)
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    invalidate()
                }
            }
            KT.SHIFT -> { p.longFired = true; listener?.onShift(true) }
            KT.LANG -> { p.longFired = true; listener?.onPicker() }
            else -> {}
        }
    }

    private fun move(id: Int, x: Float, y: Float) {
        val p = pointers.get(id) ?: return
        val kr = p.kr ?: return
        if (p.popup.isNotEmpty()) {
            val n = popupItems(p.popup).size
            val cell = max(kr.rect.width(), 38f * d)
            val idx = ((x - p.popupLeft) / cell).toInt()
            val newIdx = if (idx in 0 until n) idx else -1
            if (newIdx != p.popupIdx) { p.popupIdx = newIdx; invalidate() }
            return
        }
        if (kr.key.type == KT.SPACE) {
            val step = 22f * d
            while (x - p.lastX > step) { p.lastX += step; p.dragged = true; listener?.onMoveCursor(1) }
            while (p.lastX - x > step) { p.lastX -= step; p.dragged = true; listener?.onMoveCursor(-1) }
            return
        }
        if (p.longFired) return
        if (!kr.hit.contains(x, y)) {
            val nk = findKey(x, y)
            if (nk != null && nk !== kr && nk.key.type == KT.CHAR && kr.key.type == KT.CHAR) {
                cancelLong(p)
                p.kr = nk
                scheduleLong(p)
                invalidate()
            }
        }
    }

    private fun up(id: Int, x: Float) {
        val p = pointers.get(id) ?: return
        pointers.remove(id)
        cancelLong(p)
        cancelRepeat(p)
        val kr = p.kr
        if (kr != null) {
            val k = kr.key
            if (p.popup.isNotEmpty()) {
                val items = popupItems(p.popup)
                if (p.popupIdx in items.indices) listener?.onChar(items[p.popupIdx])
            } else if (!p.longFired) {
                when (k.type) {
                    KT.CHAR -> listener?.onChar(k.text)
                    KT.SHIFT -> listener?.onShift(false)
                    KT.ENTER -> listener?.onEnter()
                    KT.SPACE -> if (!p.dragged) listener?.onSpace()
                    KT.PAGE -> k.target?.let { listener?.onPage(it) }
                    KT.LANG -> listener?.onLang()
                    else -> {}
                }
            }
        }
        invalidate()
    }

    private fun cancelLong(p: Pointer) {
        p.longRun?.let { ui.removeCallbacks(it) }
        p.longRun = null
    }

    private fun cancelRepeat(p: Pointer) {
        p.repeatRun?.let { ui.removeCallbacks(it) }
        p.repeatRun = null
    }

    private fun cancelAll() {
        for (i in 0 until pointers.size()) {
            val p = pointers.valueAt(i)
            cancelLong(p)
            cancelRepeat(p)
        }
        pointers.clear()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelAll()
        super.onDetachedFromWindow()
    }
}
