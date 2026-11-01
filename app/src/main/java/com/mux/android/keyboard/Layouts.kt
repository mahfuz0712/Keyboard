package com.mux.android.keyboard

enum class Page { EN, SYM1, SYM2 }

enum class KT { CHAR, SHIFT, DEL, PAGE, LANG, ENTER, SPACE, GAP }

class Key(
    val type: KT,
    val label: String,
    val text: String = label,
    val weight: Float = 1f,
    val alts: String = "",
    val hint: String = "",     // small digit at the top-right corner
    val sub: String = "",      // small Bangla letter at the bottom-right corner (phonetic mode)
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

    // Extra Bangla signs on long-press while typing phonetic Bangla
    private val BN_ALTS = mapOf(
        "n" to "ংঁঃ", "t" to "ৎ", "h" to "ঃ"
    )

    // Bangla letter that each key gives in phonetic mode (shown small on the key)
    private val SUB_LOWER = mapOf(
        "a" to "আ", "b" to "ব", "c" to "চ", "d" to "দ", "e" to "এ", "f" to "ফ", "g" to "গ", "h" to "হ",
        "i" to "ই", "j" to "জ", "k" to "ক", "l" to "ল", "m" to "ম", "n" to "ন", "o" to "অ", "p" to "প",
        "q" to "ক", "r" to "র", "s" to "স", "t" to "ত", "u" to "উ", "v" to "ভ", "w" to "ও", "x" to "ক্ষ",
        "y" to "য়", "z" to "য"
    )
    private val SUB_UPPER = mapOf(
        "a" to "আ", "d" to "ড", "i" to "ঈ", "n" to "ণ", "o" to "ও", "r" to "ড়", "s" to "শ", "t" to "ট", "u" to "ঊ"
    )

    private const val LATIN_DIGITS = "1234567890"
    private const val BN_DIGITS = "০১২৩৪৫৬৭৮৯"

    class Ctx(
        val page: Page,
        val shift: Int,          // 0 off, 1 once, 2 lock (English page only)
        val numberRow: Boolean,
        val enterLabel: String,  // "" = arrow icon
        val langLabel: String,
        val bangla: Boolean      // phonetic Bangla typing is on
    )

    fun rows(c: Ctx): List<List<Key>> {
        val out = ArrayList<List<Key>>()
        when (c.page) {
            Page.EN -> {
                if (c.numberRow) out.add(row("1 2 3 4 5 6 7 8 9 0", c, null))
                out.add(row("q w e r t y u i o p", c, if (c.numberRow) null else LATIN_DIGITS))
                out.add(row("@@GAP*0.5 a s d f g h j k l @@GAP*0.5", c, null))
                out.add(row("@@SHIFT*1.5 z x c v b n m @@DEL*1.5", c, null))
                out.add(bottom(c))
            }
            Page.SYM1 -> {
                out.add(row("1 2 3 4 5 6 7 8 9 0", c, null))
                out.add(row("@ # $ % & * - + ( )", c, null))
                out.add(row("@@GO_SYM2*1.5 ! \" ' : ; / ? @@DEL*1.5", c, null))
                out.add(bottom(c))
            }
            Page.SYM2 -> {
                out.add(row("~ ` | • √ π ÷ × ¶ ∆", c, null))
                out.add(row("£ € ¥ ₹ ৳ ^ ° = { }", c, null))
                out.add(row("@@GO_SYM1*1.5 \\ < > [ ] © ® @@DEL*1.5", c, null))
                out.add(bottom(c))
            }
        }
        return out
    }

    private fun bottom(c: Ctx): List<Key> {
        val first = if (c.page == Page.EN) "@@GO_SYM1*1.5" else "@@GO_ABC*1.5"
        return row("$first @@LANG , @@SPACE*4.5 . @@ENTER*1.5", c, null)
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
        "GO_ABC" -> Key(KT.PAGE, "ABC", weight = w, target = Page.EN)
        else -> Key(KT.GAP, "", weight = w)
    }

    private fun charKey(tok: String, c: Ctx, hint: String): Key {
        val onLetters = c.page == Page.EN
        val shifted = onLetters && c.shift != 0
        val text = if (shifted) tok.uppercase() else tok
        var label = text
        var sub = ""
        var alts = ALTS[tok] ?: ""

        if (onLetters && c.bangla) {
            val isLetter = tok.length == 1 && tok[0] in 'a'..'z'
            val isDigit = tok.length == 1 && tok[0] in '0'..'9'
            if (isLetter) {
                sub = (if (shifted) SUB_UPPER[tok] else SUB_LOWER[tok]) ?: ""
                alts = BN_ALTS[tok] ?: ""
            } else if (isDigit) {
                // digit key types a Bangla digit; hold it for the Latin digit
                label = BN_DIGITS[tok[0] - '0'].toString()
                alts = tok
            } else if (tok == ".") {
                sub = "।"
                alts = "." + alts
            }
        }
        if (shifted) alts = alts.uppercase()
        if (hint.isNotEmpty()) alts = hint + alts
        return Key(KT.CHAR, label, text, 1f, alts, hint, sub)
    }
}
