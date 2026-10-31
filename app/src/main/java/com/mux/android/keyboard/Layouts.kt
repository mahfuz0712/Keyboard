package com.mux.android.keyboard

enum class Page { EN, SYM1, SYM2, BN1, BN2, BN3 }

enum class KT { CHAR, SHIFT, DEL, PAGE, LANG, ENTER, SPACE, GAP }

class Key(
    val type: KT,
    val label: String,
    val text: String = label,
    val weight: Float = 1f,
    val alts: String = "",
    val hint: String = "",
    val target: Page? = null
)

/** Builds the rows of keys for a page. Pure data, no Android classes. */
object Layouts {
    private val ALTS = mapOf(
        "a" to "àáâäæãåā", "e" to "èéêëēėę", "i" to "îïíīįì", "o" to "ôöòóœøōõ", "u" to "ûüùúū",
        "c" to "çćč", "n" to "ñń", "s" to "śšß", "y" to "ýÿ", "z" to "žźż", "l" to "ł", "d" to "ð",
        "." to "?!:;-…'\"", "," to ";:",
        "$" to "£€¥₹৳¢", "-" to "–—•", "'" to "‘’`", "\"" to "“”„", "?" to "¿", "!" to "¡",
        "/" to "\\", "(" to "[{<", ")" to "]}>", "=" to "≠≈", "%" to "‰", "+" to "±",
        "*" to "†‡", "0" to "°", "&" to "§", "#" to "№"
    )

    private const val LATIN_DIGITS = "1234567890"
    private const val BN_DIGITS = "১২৩৪৫৬৭৮৯০"
    private const val BN_MARKS = "ািীুূৃেৈোৌ্ঁ়"

    class Ctx(
        val page: Page,
        val shift: Int,          // 0 off, 1 once, 2 lock (English only)
        val numberRow: Boolean,
        val enterLabel: String,  // "" = arrow icon
        val langLabel: String,
        val letterPage: Page
    )

    fun rows(c: Ctx): List<List<Key>> {
        val out = ArrayList<List<Key>>()
        when (c.page) {
            Page.EN -> {
                if (c.numberRow) out.add(row("1 2 3 4 5 6 7 8 9 0", c, null))
                out.add(row("q w e r t y u i o p", c, if (c.numberRow) null else LATIN_DIGITS))
                out.add(row("@@GAP*0.5 a s d f g h j k l @@GAP*0.5", c, null))
                out.add(row("@@SHIFT*1.5 z x c v b n m @@DEL*1.5", c, null))
                out.add(bottom(c, "."))
            }
            Page.SYM1 -> {
                out.add(row("1 2 3 4 5 6 7 8 9 0", c, null))
                out.add(row("@ # $ % & * - + ( )", c, null))
                out.add(row("@@GO_SYM2*1.5 ! \" ' : ; / ? @@DEL*1.5", c, null))
                out.add(bottom(c, "."))
            }
            Page.SYM2 -> {
                out.add(row("~ ` | • √ π ÷ × ¶ ∆", c, null))
                out.add(row("£ € ¥ ₹ ৳ ^ ° = { }", c, null))
                out.add(row("@@GO_SYM1*1.5 \\ < > [ ] © ® @@DEL*1.5", c, null))
                out.add(bottom(c, "."))
            }
            Page.BN1 -> {
                if (c.numberRow) out.add(row("১ ২ ৩ ৪ ৫ ৬ ৭ ৮ ৯ ০", c, null))
                out.add(row("ক খ গ ঘ ঙ চ ছ জ ঝ ঞ", c, if (c.numberRow) null else BN_DIGITS))
                out.add(row("ট ঠ ড ঢ ণ ত থ দ ধ ন", c, null))
                out.add(row("@@GO_BN2 প ফ ব ভ ম য র ল @@DEL", c, null))
                out.add(bottom(c, "।"))
            }
            Page.BN2 -> {
                if (c.numberRow) out.add(row("১ ২ ৩ ৪ ৫ ৬ ৭ ৮ ৯ ০", c, null))
                out.add(row("অ আ ই ঈ উ ঊ ঋ এ ঐ ও", c, if (c.numberRow) null else BN_DIGITS))
                out.add(row("ঔ া ি ী ু ূ ৃ ে ৈ ো", c, null))
                out.add(row("@@GO_BN3 ৌ ্ ় ঁ ং ঃ । ৳ @@DEL", c, null))
                out.add(bottom(c, "।"))
            }
            Page.BN3 -> {
                if (c.numberRow) out.add(row("১ ২ ৩ ৪ ৫ ৬ ৭ ৮ ৯ ০", c, null))
                out.add(row("শ ষ স হ ড় ঢ় য় ক্ষ জ্ঞ ত্র", c, if (c.numberRow) null else BN_DIGITS))
                out.add(row("ক্ক ঙ্গ ন্দ ন্ত স্ত ক্ত ্র ্য র্ ৎ", c, null))
                out.add(row("@@GO_BN1 ! ? , - ' \" : ; @@DEL", c, null))
                out.add(bottom(c, "।"))
            }
        }
        return out
    }

    private fun bottom(c: Ctx, dot: String): List<Key> {
        val first = if (c.page == Page.SYM1 || c.page == Page.SYM2) "@@GO_ABC*1.5" else "@@GO_SYM1*1.5"
        return row("$first @@LANG , @@SPACE*4.5 $dot @@ENTER*1.5", c, null)
    }

    private fun row(spec: String, c: Ctx, digitHints: String?): List<Key> {
        val keys = ArrayList<Key>()
        var charIndex = 0
        for (tok in spec.split(' ')) {
            if (tok.isEmpty()) continue
            if (tok.startsWith("@@")) {
                val parts = tok.substring(2).split('*')
                val name = parts[0]
                val w = if (parts.size > 1) parts[1].toFloat() else 1f
                keys.add(special(name, w, c))
            } else {
                val hint = if (digitHints != null && charIndex < digitHints.length) digitHints[charIndex].toString() else ""
                keys.add(charKey(tok, c, hint))
                charIndex++
            }
        }
        return keys
    }

    private fun special(name: String, w: Float, c: Ctx): Key = when (name) {
        "SHIFT" -> Key(KT.SHIFT, "", weight = w)
        "DEL" -> Key(KT.DEL, "", weight = w)
        "SPACE" -> Key(KT.SPACE, "", " ", w)
        "ENTER" -> Key(KT.ENTER, c.enterLabel, "\n", w)
        "LANG" -> Key(KT.LANG, c.langLabel, weight = w)
        "GAP" -> Key(KT.GAP, "", weight = w)
        "GO_SYM1" -> Key(KT.PAGE, "?123", weight = w, target = Page.SYM1)
        "GO_SYM2" -> Key(KT.PAGE, "=\\<", weight = w, target = Page.SYM2)
        "GO_ABC" -> Key(KT.PAGE, if (c.letterPage == Page.EN) "ABC" else "অআক", weight = w, target = c.letterPage)
        "GO_BN1" -> Key(KT.PAGE, "১/৩", weight = w, target = Page.BN1)
        "GO_BN2" -> Key(KT.PAGE, "২/৩", weight = w, target = Page.BN2)
        "GO_BN3" -> Key(KT.PAGE, "৩/৩", weight = w, target = Page.BN3)
        else -> Key(KT.GAP, "", weight = w)
    }

    private fun charKey(tok: String, c: Ctx, hint: String): Key {
        val isEn = c.page == Page.EN
        val shifted = isEn && c.shift != 0
        val text = if (shifted) tok.uppercase() else tok
        var label = text
        if (c.page == Page.BN2 || c.page == Page.BN3) {
            if (tok.isNotEmpty() && BN_MARKS.indexOf(tok[0]) >= 0) label = "◌" + tok
        }
        var alts = ALTS[tok] ?: ""
        if (shifted) alts = alts.uppercase()
        if (hint.isNotEmpty()) alts = hint + alts
        return Key(KT.CHAR, label, text, 1f, alts, hint)
    }
}
