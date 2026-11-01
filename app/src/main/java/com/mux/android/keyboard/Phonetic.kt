package com.mux.android.keyboard

/**
 * Avro-style phonetic Bangla: type English letters, get Bangla letters.
 *   ami banglay gan gai.  ->  আমি বাংলায় গান গাই।
 * Case matters like in Avro: T D N R S Sh O I U give the "hard" letters.
 */
object Phonetic {
    private const val C = 0   // consonant
    private const val V = 1   // vowel (independent form, kar form)
    private const val S = 2   // sign (ং ঃ ঁ ৎ । ...)
    private const val L = 3   // literal (digits)
    private const val Y = 4   // y: য় at word start, ্য after a consonant

    private class Tok(val kind: Int, val a: String, val b: String)

    private val table = HashMap<String, Tok>()

    class Suggestion(val best: String, val list: List<String>)

    init {
        val cons = listOf(
            "k" to "ক", "kh" to "খ", "g" to "গ", "gh" to "ঘ", "Ng" to "ঙ",
            "c" to "চ", "ch" to "ছ", "chh" to "ছ", "j" to "জ", "jh" to "ঝ", "NG" to "ঞ",
            "T" to "ট", "Th" to "ঠ", "D" to "ড", "Dh" to "ঢ", "N" to "ণ",
            "t" to "ত", "th" to "থ", "d" to "দ", "dh" to "ধ", "n" to "ন",
            "p" to "প", "ph" to "ফ", "f" to "ফ", "b" to "ব", "bh" to "ভ", "v" to "ভ", "m" to "ম",
            "z" to "য", "r" to "র", "l" to "ল",
            "sh" to "শ", "S" to "শ", "Sh" to "ষ", "s" to "স", "h" to "হ",
            "R" to "ড়", "Rh" to "ঢ়", "q" to "ক", "x" to "ক্ষ", "GG" to "জ্ঞ"
        )
        for ((k, v) in cons) table[k] = Tok(C, v, "")

        table["y"] = Tok(Y, "", "")
        table["Y"] = Tok(Y, "", "")

        val vowels = listOf(
            Triple("rri", "ঋ", "ৃ"), Triple("oi", "ঐ", "ৈ"), Triple("ou", "ঔ", "ৌ"),
            Triple("aa", "আ", "া"), Triple("ii", "ঈ", "ী"), Triple("ee", "ঈ", "ী"),
            Triple("uu", "ঊ", "ূ"), Triple("oo", "ঊ", "ূ"),
            Triple("a", "আ", "া"), Triple("A", "আ", "া"),
            Triple("i", "ই", "ি"), Triple("I", "ঈ", "ী"),
            Triple("u", "উ", "ু"), Triple("U", "ঊ", "ূ"),
            Triple("e", "এ", "ে"), Triple("E", "এ", "ে"),
            Triple("o", "অ", ""), Triple("O", "ও", "ো"), Triple("w", "ও", "ো")
        )
        for ((k, a, b) in vowels) table[k] = Tok(V, a, b)

        val signs = listOf(
            "ng" to "ং", ":" to "ঃ", "^" to "ঁ", "t``" to "ৎ", "$" to "৳", "." to "।", ".." to "."
        )
        for ((k, v) in signs) table[k] = Tok(S, v, "")

        val digits = "০১২৩৪৫৬৭৮৯"
        for (i in 0..9) table[i.toString()] = Tok(L, digits[i].toString(), "")
    }

    /** Converts what was typed (English letters) to Bangla. */
    fun convert(raw: String): String {
        val out = StringBuilder()
        var last = 0   // 0 start, 1 consonant, 2 vowel, 3 other
        var i = 0
        while (i < raw.length) {
            var found: Tok? = null
            var len = 0
            var n = minOf(3, raw.length - i)
            while (n > 0) {
                val t = table[raw.substring(i, i + n)]
                if (t != null) {
                    found = t
                    len = n
                    break
                }
                n--
            }
            if (found == null) {
                out.append(raw[i])
                last = 3
                i++
                continue
            }
            var cur: Tok = found
            if (len == 2 && raw[i] == 'n' && raw[i + 1] == 'g') {
                val nx = if (i + 2 < raw.length) raw[i + 2] else ' '
                if ("kgaeiouAEIOU".indexOf(nx) >= 0) cur = table["Ng"] ?: cur
            }
            when (cur.kind) {
                C -> {
                    if (last == 1) out.append('্')
                    out.append(cur.a)
                    last = 1
                }
                V -> {
                    out.append(if (last == 1) cur.b else cur.a)
                    last = 2
                }
                Y -> {
                    out.append(if (last == 1) "্য" else "য়")
                    last = 1
                }
                else -> {
                    out.append(cur.a)
                    last = 3
                }
            }
            i += len
        }
        return out.toString()
    }

    // ------------------------------------------------------------ suggestions

    private val subs = listOf(
        "sh" to "s", "s" to "sh", "s" to "S", "S" to "s",
        "t" to "T", "T" to "t", "d" to "D", "D" to "d", "n" to "N", "N" to "n",
        "o" to "O", "O" to "o", "i" to "I", "I" to "i", "u" to "U", "U" to "u",
        "r" to "R", "R" to "r", "z" to "j", "j" to "z", "c" to "ch", "ch" to "c"
    )

    private fun expand(src: List<String>, limit: Int): List<String> {
        val out = LinkedHashSet<String>()
        for (r in src) {
            for ((a, b) in subs) {
                var i = r.indexOf(a)
                while (i >= 0) {
                    val skip = a == "n" && i + 1 < r.length && r[i + 1] == 'g'
                    if (!skip) {
                        out.add(r.substring(0, i) + b + r.substring(i + a.length))
                        if (out.size >= limit) return out.toList()
                    }
                    i = r.indexOf(a, i + 1)
                }
            }
        }
        return out.toList()
    }

    /**
     * best = what will be committed on space (the typed word, or a known dictionary word
     * that differs only by case-style letters, like Avro's auto-correct: tomake -> তোমাকে).
     * list = what the suggestion strip shows (best first).
     */
    fun suggest(raw: String): Suggestion {
        val primary = convert(raw)
        var hasLetter = false
        for (ch in raw) if (ch in 'a'..'z' || ch in 'A'..'Z') hasLetter = true
        if (!hasLetter) return Suggestion(primary, listOf(primary))

        val l1 = expand(listOf(raw), 60).filter { it != raw }
        val l2 = if (raw.length <= 9) expand(l1.take(30), 120).filter { it != raw && !l1.contains(it) } else emptyList()
        val v1 = l1.map { convert(it) }.distinct()
        val v2 = l2.map { convert(it) }.distinct()

        var best = primary
        if (!BanglaDict.has(primary)) {
            best = v1.firstOrNull { BanglaDict.has(it) } ?: v2.firstOrNull { BanglaDict.has(it) } ?: primary
        }

        val out = LinkedHashSet<String>()
        out.add(best)
        out.add(primary)
        for (w in BanglaDict.complete(best, 3)) out.add(w)
        for (v in v1) if (BanglaDict.has(v)) out.add(v)
        for (v in v1) out.add(v)
        return Suggestion(best, out.take(5))
    }
}
